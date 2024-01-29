package com.simprints.infra.eventsync.sync.up.tasks

import com.fasterxml.jackson.core.JsonParseException
import com.google.common.truth.Truth.assertThat
import com.simprints.core.tools.json.JsonHelper
import com.simprints.core.tools.time.TimeHelper
import com.simprints.core.tools.utils.randomUUID
import com.simprints.infra.authstore.AuthStore
import com.simprints.infra.config.store.ConfigRepository
import com.simprints.infra.config.store.models.ProjectConfiguration
import com.simprints.infra.config.store.models.SynchronizationConfiguration
import com.simprints.infra.config.store.models.UpSynchronizationConfiguration
import com.simprints.infra.events.EventRepository
import com.simprints.infra.events.sampledata.*
import com.simprints.infra.events.sampledata.SampleDefaults.DEFAULT_PROJECT_ID
import com.simprints.infra.events.sampledata.SampleDefaults.GUID1
import com.simprints.infra.events.sampledata.SampleDefaults.GUID2
import com.simprints.infra.eventsync.SampleSyncScopes
import com.simprints.infra.eventsync.event.remote.EventRemoteDataSource
import com.simprints.infra.eventsync.exceptions.TryToUploadEventsForNotSignedProject
import com.simprints.infra.eventsync.status.up.EventUpSyncScopeRepository
import com.simprints.infra.eventsync.status.up.domain.EventUpSyncOperation
import com.simprints.infra.eventsync.status.up.domain.EventUpSyncOperation.UpSyncState
import com.simprints.infra.network.exceptions.NetworkConnectionException
import com.simprints.testtools.common.syntax.assertThrows
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

internal class EventUpSyncTaskTest {

    private val operation = SampleSyncScopes.projectUpSyncScope.operation

    private lateinit var eventUpSyncTask: EventUpSyncTask

    @MockK
    private lateinit var eventUpSyncScopeRepository: EventUpSyncScopeRepository

    @MockK
    lateinit var authStore: AuthStore

    @MockK
    lateinit var eventRepo: EventRepository

    @MockK
    lateinit var eventRemoteDataSource: EventRemoteDataSource

    @MockK
    private lateinit var timeHelper: TimeHelper

    @MockK
    private lateinit var synchronizationConfiguration: SynchronizationConfiguration

    @MockK
    private lateinit var projectConfiguration: ProjectConfiguration

    @MockK
    private lateinit var configRepository: ConfigRepository

    @Before
    fun setUp() {
        MockKAnnotations.init(this, relaxed = true)

        every { timeHelper.now() } returns NOW
        every { authStore.signedInProjectId } returns DEFAULT_PROJECT_ID

        every { projectConfiguration.synchronization } returns synchronizationConfiguration
        coEvery { configRepository.getProjectConfiguration() } returns projectConfiguration

        eventUpSyncTask = EventUpSyncTask(
            authStore = authStore,
            eventUpSyncScopeRepo = eventUpSyncScopeRepository,
            eventRepository = eventRepo,
            eventRemoteDataSource = eventRemoteDataSource,
            timeHelper = timeHelper,
            configRepository = configRepository,
            jsonHelper = JsonHelper,
        )
    }

    @Test
    fun `upload fetches events for all provided closed sessions`() = runTest {
        setUpSyncKind(UpSynchronizationConfiguration.UpSynchronizationKind.NONE)

        coEvery { eventRepo.getAllClosedSessions() } returns listOf(
            createSessionScope(GUID1),
            createSessionScope(GUID2)
        )
        coEvery {
            eventRepo.getEventsFromSession(GUID1)
        } returns listOf(createSessionCaptureEvent(GUID1))
        coEvery {
            eventRepo.getEventsFromSession(GUID2)
        } returns listOf(createSessionCaptureEvent(GUID2))

        eventUpSyncTask.upSync(operation).toList()

        coVerify(exactly = 2) { eventRepo.getEventsFromSession(any()) }
    }

    @Test
    fun `upload should not filter any events on upload`() = runTest {
        setUpSyncKind(UpSynchronizationConfiguration.UpSynchronizationKind.ALL)
        coEvery { eventRepo.getAllClosedSessions() } returns listOf(createSessionScope(GUID1))
        coEvery { eventRepo.getEventsFromSession(any()) } returns listOf(
            createAuthenticationEvent(),
            createEnrolmentEventV2(),
        )

        eventUpSyncTask.upSync(operation).toList()

        coVerify {
            eventRemoteDataSource.post(
                any(),
                withArg {
                    val (scope, events) = it.entries.first()
                    assertThat(scope.id).isEqualTo(GUID1)
                    assertThat(events).hasSize(2)
                },
                any()
            )
        }
    }

    @Test
    fun `upload should filter biometric events on upload`() = runTest {
        setUpSyncKind(UpSynchronizationConfiguration.UpSynchronizationKind.ONLY_BIOMETRICS)

        coEvery { eventRepo.getAllClosedSessions() } returns listOf(createSessionScope(GUID1))
        coEvery { eventRepo.getEventsFromSession(any()) } returns listOf(
            createAuthenticationEvent(),
            createAlertScreenEvent(),
            // only following should be uploaded
            createEnrolmentEventV2(),
            createPersonCreationEvent(),
            createFingerprintCaptureBiometricsEvent(),
            createFaceCaptureBiometricsEvent(),
        )

        eventUpSyncTask.upSync(operation).toList()

        coVerify {
            eventRemoteDataSource.post(
                any(),
                withArg {
                    val (scope, events) = it.entries.first()
                    assertThat(scope.id).isEqualTo(GUID1)
                    assertThat(events).hasSize(4)
                },
                any()
            )
        }
    }

    @Test
    fun `upload should filter analytics events on upload`() = runTest {
        setUpSyncKind(UpSynchronizationConfiguration.UpSynchronizationKind.ONLY_ANALYTICS)

        coEvery { eventRepo.getAllClosedSessions() } returns listOf(createSessionScope(GUID1))
        coEvery { eventRepo.getEventsFromSession(any()) } returns listOf(
            createFingerprintCaptureBiometricsEvent(),
            createFaceCaptureBiometricsEvent(),
            // only following should be uploaded
            createPersonCreationEvent(),
            createEnrolmentEventV2(),
            createAlertScreenEvent(),
        )

        eventUpSyncTask.upSync(operation).toList()

        coVerify {
            eventRemoteDataSource.post(
                any(),
                withArg {
                    val (scope, events) = it.entries.first()
                    assertThat(scope.id).isEqualTo(GUID1)
                    assertThat(events).hasSize(3)
                },
                any()
            )
        }
    }

    @Test
    fun `should not upload sessions for not signed project`() = runTest {
        assertThrows<TryToUploadEventsForNotSignedProject> {
            eventUpSyncTask.upSync(EventUpSyncOperation(randomUUID())).toList()
        }
    }

    @Test
    fun `when upload succeeds it should delete events`() = runTest {
        setUpSyncKind(UpSynchronizationConfiguration.UpSynchronizationKind.ALL)

        coEvery { eventRepo.getAllClosedSessions() } returns listOf(
            createSessionScope(GUID1),
            createSessionScope(GUID2)
        )
        coEvery {
            eventRepo.getEventsFromSession(GUID1)
        } returns listOf(createSessionCaptureEvent(GUID1))
        coEvery {
            eventRepo.getEventsFromSession(GUID2)
        } returns listOf(createSessionCaptureEvent(GUID2))

        eventUpSyncTask.upSync(operation).toList()

        coVerify {
            eventRepo.deleteSession(GUID1)
            eventRepo.deleteSession(GUID2)
        }
    }

    @Test
    fun `upload in progress should emit progress`() = runTest {
        setUpSyncKind(UpSynchronizationConfiguration.UpSynchronizationKind.NONE)

        coEvery { eventRepo.getAllClosedSessions() } returns listOf(
            createSessionScope(GUID1),
            createSessionScope(GUID2)
        )
        coEvery { eventRepo.getEventsFromSession(GUID1) } returns listOf(
            createSessionCaptureEvent(GUID1),
        )
        coEvery { eventRepo.getEventsFromSession(GUID2) } returns listOf(
            createEnrolmentEventV2(),
            createAlertScreenEvent(),
        )

        val progress = eventUpSyncTask.upSync(operation).toList()

        assertThat(progress[0].operation.lastState).isEqualTo(UpSyncState.RUNNING)
        assertThat(progress[1].operation.lastState).isEqualTo(UpSyncState.RUNNING)
        assertThat(progress[2].operation.lastState).isEqualTo(UpSyncState.COMPLETE)
    }

    @Test
    fun `when upload fails due to generic error should not delete events`() = runTest {
        setUpSyncKind(UpSynchronizationConfiguration.UpSynchronizationKind.NONE)

        coEvery { eventRepo.getAllClosedSessions() } returns listOf(createSessionScope(GUID1))
        coEvery {
            eventRepo.getEventsFromSession(GUID1)
        } returns listOf(createSessionCaptureEvent(GUID1))

        coEvery { eventRemoteDataSource.post(any(), any()) } throws Throwable("")

        eventUpSyncTask.upSync(operation).toList()

        coVerify(exactly = 0) { eventRepo.deleteSession(any()) }
    }

    @Test
    fun `when upload fails due to network issue should not delete events`() = runTest {
        setUpSyncKind(UpSynchronizationConfiguration.UpSynchronizationKind.NONE)

        coEvery { eventRepo.getAllClosedSessions() } returns listOf(createSessionScope(GUID1))
        coEvery {
            eventRepo.getEventsFromSession(GUID1)
        } returns listOf(createSessionCaptureEvent(GUID1))

        coEvery { eventRemoteDataSource.post(any(), any()) } throws NetworkConnectionException(
            cause = Exception()
        )

        eventUpSyncTask.upSync(operation).toList()

        coVerify(exactly = 0) { eventRepo.deleteSession(any()) }
    }

    @Test
    fun `upload should not dump events when fetch fails`() = runTest {
        setUpSyncKind(UpSynchronizationConfiguration.UpSynchronizationKind.ALL)

        coEvery { eventRepo.getAllClosedSessions() } returns listOf(createSessionScope(GUID1))
        coEvery { eventRepo.getEventsFromSession(GUID1) } throws IllegalStateException()

        eventUpSyncTask.upSync(operation).toList()

        coVerify(exactly = 0) {
            eventRemoteDataSource.post(any(), any())
            eventRemoteDataSource.dumpInvalidEvents(any(), any())
            eventRepo.deleteSession(GUID1)
        }
    }

    @Test
    fun `upload should dump invalid events and delete the events`() = runTest {
        setUpSyncKind(UpSynchronizationConfiguration.UpSynchronizationKind.ALL)

        coEvery { eventRepo.getAllClosedSessions() } returns listOf(createSessionScope(GUID1))
        coEvery {
            eventRepo.getEventsFromSession(GUID1)
        } throws JsonParseException(mockk(relaxed = true), "")

        coEvery { eventRepo.getEventsJsonFromSession(GUID1) } returns listOf("{}")

        eventUpSyncTask.upSync(operation).toList()

        coVerify(exactly = 0) { eventRemoteDataSource.post(any(), any()) }
        coVerify {
            eventRepo.getEventsJsonFromSession(any())
            eventRemoteDataSource.dumpInvalidEvents(any(), any())
            eventRepo.deleteSession(GUID1)
        }
    }

    @Test
    fun `fail dump of invalid events should not delete the events`() = runTest {
        setUpSyncKind(UpSynchronizationConfiguration.UpSynchronizationKind.ALL)

        coEvery { eventRepo.getAllClosedSessions() } returns listOf(createSessionScope(GUID1))
        coEvery {
            eventRepo.getEventsFromSession(GUID1)
        } throws JsonParseException(mockk(relaxed = true), "")
        coEvery { eventRepo.getEventsJsonFromSession(GUID1) } returns listOf("{}")
        coEvery { eventRemoteDataSource.dumpInvalidEvents(any(), any()) } throws HttpException(
            Response.error<String>(503, "".toResponseBody(null))
        )

        eventUpSyncTask.upSync(operation).toList()

        coVerify(exactly = 0) {
            eventRemoteDataSource.post(any(), any())
            eventRepo.deleteSession(GUID1)
        }

        coVerify {
            eventRepo.getEventsJsonFromSession(any())
            eventRemoteDataSource.dumpInvalidEvents(any(), any())
        }
    }

    @Test
    fun `upSync should emit a failure if upload fails`() = runTest {
        coEvery { eventRepo.getAllClosedSessions() } throws IllegalStateException()

        val progress = eventUpSyncTask.upSync(operation).toList()
        assertThat(progress.first().operation.lastState).isEqualTo(UpSyncState.FAILED)
        coVerify(exactly = 1) { eventUpSyncScopeRepository.insertOrUpdate(any()) }
    }

    private fun setUpSyncKind(kind: UpSynchronizationConfiguration.UpSynchronizationKind) {
        every { synchronizationConfiguration.up.simprints.kind } returns kind
    }

    companion object {

        private const val NOW = 1000L
    }
}
