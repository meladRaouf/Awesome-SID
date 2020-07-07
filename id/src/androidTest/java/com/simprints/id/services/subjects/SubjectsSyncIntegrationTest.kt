@file:Suppress("DEPRECATION")

package com.simprints.id.services.subjects

import android.app.Activity
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.simprints.core.tools.json.JsonHelper
import com.simprints.id.Application
import com.simprints.id.activities.requestLogin.RequestLoginActivity
import com.simprints.id.commontesttools.AndroidDefaultTestConstants.DEFAULT_LOCAL_DB_KEY
import com.simprints.id.commontesttools.DefaultTestConstants.DEFAULT_PROJECT_ID
import com.simprints.id.commontesttools.DefaultTestConstants.DEFAULT_USER_ID
import com.simprints.id.commontesttools.DefaultTestConstants.moduleSyncScope
import com.simprints.id.commontesttools.DefaultTestConstants.projectSyncScope
import com.simprints.id.commontesttools.DefaultTestConstants.userSyncScope
import com.simprints.id.commontesttools.EnrolmentRecordsGeneratorUtils.getRandomEnrolmentEvents
import com.simprints.id.commontesttools.SubjectsGeneratorUtils.getRandomPeople
import com.simprints.id.commontesttools.di.TestAppModule
import com.simprints.id.commontesttools.di.TestDataModule
import com.simprints.id.commontesttools.di.TestSyncModule
import com.simprints.id.data.db.common.RemoteDbManager
import com.simprints.id.data.db.subjects_sync.down.SubjectsDownSyncScopeRepository
import com.simprints.id.data.db.subjects_sync.down.domain.SubjectsDownSyncScope
import com.simprints.id.data.db.event.domain.events.EventPayloadType.ENROLMENT_RECORD_CREATION
import com.simprints.id.data.db.subject.local.SubjectLocalDataSource
import com.simprints.id.data.db.subject.remote.EventRemoteInterface
import com.simprints.id.data.db.event.remote.events.ApiEventCount
import com.simprints.id.data.db.event.remote.events.ApiEvent
import com.simprints.id.data.db.event.remote.events.ApiEventPayloadType
import com.simprints.id.data.loginInfo.LoginInfoManager
import com.simprints.id.data.secure.LegacyLocalDbKeyProvider
import com.simprints.id.data.secure.SecureLocalDbKeyProvider
import com.simprints.id.network.BaseUrlProvider
import com.simprints.id.network.SimApiClientFactoryImpl
import com.simprints.id.services.scheduledSync.subjects.master.SubjectsSyncManager
import com.simprints.id.services.scheduledSync.subjects.master.models.SubjectsSyncState
import com.simprints.id.services.scheduledSync.subjects.master.models.SubjectsSyncWorkerState.*
import com.simprints.id.testtools.AndroidTestConfig
import com.simprints.testtools.android.runOnActivity
import com.simprints.testtools.common.di.DependencyRule
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runBlockingTest
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

@RunWith(AndroidJUnit4::class)
@ExperimentalCoroutinesApi
class SubjectsSyncIntegrationTest {

    companion object {
        const val N_TO_DOWNLOAD_PER_MODULE = 100
        const val N_TO_UPLOAD = 10
    }

    private var mockServer = MockWebServer()
    private var mockDispatcher = MockDispatcher()

    private val app: Application = ApplicationProvider.getApplicationContext()

    @Inject lateinit var subjectLocalDataSourceSpy: SubjectLocalDataSource
    @Inject lateinit var downSyncScopeRepositorySpy: SubjectsDownSyncScopeRepository
    @Inject lateinit var subjectsSyncManager: SubjectsSyncManager

    @MockK lateinit var loginInfoManagerMock: LoginInfoManager
    @MockK lateinit var secureLocalDbKeyProviderMock: SecureLocalDbKeyProvider
    @MockK lateinit var legacyLocalDbKeyProviderMock: LegacyLocalDbKeyProvider
    @MockK lateinit var remoteDbManager: RemoteDbManager

    private val appModule by lazy {
        TestAppModule(
            app,
            legacyLocalDbKeyProviderRule = DependencyRule.ReplaceRule { legacyLocalDbKeyProviderMock },
            secureDataManagerRule = DependencyRule.ReplaceRule { secureLocalDbKeyProviderMock },
            loginInfoManagerRule = DependencyRule.ReplaceRule { loginInfoManagerMock })
    }

    private val dataModule by lazy {
        TestDataModule(
            personLocalDataSourceRule = DependencyRule.SpykRule)
    }

    private val syncModule by lazy {
        TestSyncModule(peopleDownSyncScopeRepositoryRule = DependencyRule.SpykRule)
    }

    @Before
    fun setUp() {
        MockKAnnotations.init(this, relaxUnitFun = true)
        AndroidTestConfig(this, appModule = appModule, dataModule = dataModule, syncModule = syncModule).fullSetup()

        mockServer.start()
        mockServer.dispatcher = mockDispatcher

        val mockBaseUrlProvider = mockk<BaseUrlProvider>()
        every { mockBaseUrlProvider.getApiBaseUrl() } returns mockServer.url("/").toString()
        coEvery { remoteDbManager.getCurrentToken() } returns "token"

        runBlocking {
            val remotePeopleApi = SimApiClientFactoryImpl(
                mockBaseUrlProvider,
                "deviceId",
                remoteDbManager
            ).buildClient(EventRemoteInterface::class)

            every { downSyncScopeRepositorySpy.getDownSyncScope() } returns projectSyncScope
            every { secureLocalDbKeyProviderMock.getLocalDbKeyOrThrow(any()) } returns DEFAULT_LOCAL_DB_KEY
            every { legacyLocalDbKeyProviderMock.getLocalDbKeyOrThrow(any()) } returns DEFAULT_LOCAL_DB_KEY
            every { loginInfoManagerMock.getSignedInProjectIdOrEmpty() } returns DEFAULT_PROJECT_ID
            every { loginInfoManagerMock.getSignedInUserIdOrEmpty() } returns DEFAULT_USER_ID
        }

    }

    @Test
    fun syncByProjectSuccessfully() = runBlockingTest {
        runSyncTest { activity ->
            mockUploadPeople()
            val total = mockResponsesForSync(projectSyncScope)

            runAndVerifySyncSucceeds(activity, total + N_TO_UPLOAD)
        }
    }

    @Test
    fun syncByUserSuccessfully() = runBlockingTest {
        runSyncTest { activity ->
            val total = mockResponsesForSync(userSyncScope)
            runAndVerifySyncSucceeds(activity, total)
        }
    }


    @Test
    fun syncByModuleSuccessfully() = runBlockingTest {
        runSyncTest { activity ->
            val total = mockResponsesForSync(moduleSyncScope)
            runAndVerifySyncSucceeds(activity, total)
        }
    }

    @Test
    fun uploadFailsBecauseANotCloudIssue_shouldSyncRetry() = runBlockingTest {
        runSyncTest { activity ->
            mockResponsesForSync(projectSyncScope)
            mockUploadPeople()
            mockDispatcher.uploadResponse = 300 to ""

            runAndVerifySyncRetries(activity)
        }
    }

    @Test
    fun uploadFailsBecauseACloudIssue_shouldSyncFail() = runBlockingTest {
        runSyncTest { activity ->
            mockResponsesForSync(projectSyncScope)
            mockUploadPeople()
            mockDispatcher.uploadResponse = 505 to ""

            runAndVerifySyncFails(activity)
        }
    }

    @Test
    fun downloadFailsBecauseACloudIssue_shouldSyncRetry() = runBlockingTest {
        runSyncTest { activity ->
            mockResponsesForSync(projectSyncScope)
            mockDispatcher.downResponse = 300 to listOf()

            runAndVerifySyncRetries(activity)
        }
    }

    @Test
    fun downloadFailsBecauseACloudIssue_shouldSyncFail() = runBlockingTest {
        runSyncTest { activity ->
            mockResponsesForSync(projectSyncScope)
            mockDispatcher.downResponse = 505 to listOf()

            runAndVerifySyncFails(activity)
        }
    }

    @Test
    fun downCountFailsBecauseANotCloudIssue_shouldSyncSucceed() = runBlockingTest {
        runSyncTest { activity ->
            mockResponsesForSync(projectSyncScope)
            mockDispatcher.countResponse = 300 to null

            runAndVerifySyncRetries(activity)
        }
    }

    @Test
    fun downCountFailsBecauseANotCloudIssue_shouldSyncRetry() = runBlockingTest {
        runSyncTest { activity ->
            mockResponsesForSync(projectSyncScope)
            mockDispatcher.countResponse = 300 to null

            runAndVerifySyncRetries(activity)
        }
    }


    private fun runAndVerifySyncSucceeds(act: Activity, total: Int) {
        subjectsSyncManager.getLastSyncState().observe(act as LifecycleOwner, Observer {
            if (!(it.anySyncWorkersStillRunning() || it.anySyncWorkersEnqueued())) {
                it.assertSyncSucceeds(total)
            }
        })

        subjectsSyncManager.sync()
    }

    private fun runAndVerifySyncFails(act: Activity) {
        subjectsSyncManager.getLastSyncState().observe(act as LifecycleOwner, Observer {
            if (!(it.anySyncWorkersStillRunning() || it.anySyncWorkersEnqueued())) {
                it.assertSyncFails()
            }
        })

        subjectsSyncManager.sync()
    }

    private fun runAndVerifySyncRetries(act: Activity) {
        subjectsSyncManager.getLastSyncState().observe(act as LifecycleOwner, Observer {
            if (!it.anySyncWorkersStillRunning()) {
                it.assertSyncRetries()
            }
        })

        subjectsSyncManager.sync()
    }


    private suspend fun runSyncTest(timeout: Long = 10000, block: (act: Activity) -> Unit) =
        withTimeout(timeout) {
            runOnActivity<RequestLoginActivity> {
                block(it)
            }
        }


private fun mockResponsesForSync(scope: SubjectsDownSyncScope): Int {
    val ops = runBlocking { downSyncScopeRepositorySpy.getDownSyncOperations(scope) }
    val eventsToDownload = ops.map {
        val eventsToDownload = getRandomEnrolmentEvents(
            N_TO_DOWNLOAD_PER_MODULE,
            it.projectId,
            it.attendantId ?: "",
            it.moduleId ?: "",
            ENROLMENT_RECORD_CREATION
        )

        eventsToDownload.map { event ->
            event.fromDomainToApi()
        }
    }.flatten()

    val countResponse = ApiEventCount(ApiEventPayloadType.ENROLMENT_RECORD_CREATION, eventsToDownload.size)

    mockDispatcher.downResponse = 200 to eventsToDownload
    mockDispatcher.countResponse = 200 to countResponse

    return eventsToDownload.size
}


private fun mockUploadPeople() {
    val ops = runBlocking { downSyncScopeRepositorySpy.getDownSyncOperations(projectSyncScope) }
    coEvery { subjectLocalDataSourceSpy.load(any()) } returns getRandomPeople(N_TO_UPLOAD, ops.first(), listOf(true)).asFlow()
}
}

class MockDispatcher : Dispatcher() {

    var countResponse: Pair<Int, ApiEventCount?>? = null
    var downResponse: Pair<Int, List<ApiEvent>?>? = null
    var uploadResponse: Pair<Int, String>? = null

    override fun dispatch(request: RecordedRequest): MockResponse {
        val lastPart = request.requestUrl?.pathSegments?.last()

        return if (lastPart == "events" && request.method == "POST") {
            val code = uploadResponse?.first ?: 200
            MockResponse().setResponseCode(code)
        } else if (lastPart == "count") {
            val code = countResponse?.first ?: 200
            val response = JsonHelper.gson.toJson(countResponse?.second ?: "")
            MockResponse().setResponseCode(code).setBody(response)
        } else if (lastPart == "events" && request.method == "GET") {
            val code = downResponse?.first ?: 200
            val response = JsonHelper.gson.toJson(downResponse?.second ?: "")
            MockResponse().setResponseCode(code).setBody(response)
        } else {
            MockResponse().setResponseCode(404)
        }
    }
}

private fun SubjectsSyncState.anySyncWorkersEnqueued(): Boolean =
    downSyncWorkersInfo.plus(upSyncWorkersInfo).any { it.state is Enqueued }

private fun SubjectsSyncState.anySyncWorkersStillRunning(): Boolean =
    downSyncWorkersInfo.plus(upSyncWorkersInfo).any { it.state is Running }

private fun SubjectsSyncState.assertSyncRetries() {
    assertThat((downSyncWorkersInfo.plus(upSyncWorkersInfo)).any { it.state is Enqueued }).isTrue()
}

private fun SubjectsSyncState.assertSyncFails() {
    assertThat((downSyncWorkersInfo.plus(upSyncWorkersInfo)).any { it.state is Failed }).isTrue()
}


private fun SubjectsSyncState.assertSyncSucceeds(total: Int) {
    assertThat(total).isEqualTo(total)
    assertThat(progress).isEqualTo(total)
    assertThat((downSyncWorkersInfo.plus(upSyncWorkersInfo)).all { it.state is Succeeded }).isTrue()
}
