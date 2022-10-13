package com.simprints.id.services.sync.events.master

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.*
import androidx.work.WorkInfo.State.BLOCKED
import androidx.work.WorkInfo.State.ENQUEUED
import androidx.work.testing.TestListenableWorkerBuilder
import com.google.common.truth.Truth.assertThat
import com.simprints.id.services.sync.events.common.*
import com.simprints.id.services.sync.events.down.workers.EventDownSyncCountWorker
import com.simprints.id.services.sync.events.down.workers.EventDownSyncDownloaderWorker
import com.simprints.id.services.sync.events.master.models.EventSyncWorkerType
import com.simprints.id.services.sync.events.master.models.EventSyncWorkerType.*
import com.simprints.id.services.sync.events.master.models.EventSyncWorkerType.Companion.tagForType
import com.simprints.id.services.sync.events.master.workers.EventEndSyncReporterWorker
import com.simprints.id.services.sync.events.master.workers.EventStartSyncReporterWorker
import com.simprints.id.services.sync.events.master.workers.EventSyncMasterWorker
import com.simprints.id.services.sync.events.master.workers.EventSyncMasterWorker.Companion.MASTER_SYNC_SCHEDULER_ONE_TIME
import com.simprints.id.services.sync.events.master.workers.EventSyncMasterWorker.Companion.MASTER_SYNC_SCHEDULER_PERIODIC_TIME
import com.simprints.id.services.sync.events.master.workers.EventSyncMasterWorker.Companion.OUTPUT_LAST_SYNC_ID
import com.simprints.id.services.sync.events.up.workers.EventUpSyncCountWorker
import com.simprints.id.services.sync.events.up.workers.EventUpSyncUploaderWorker
import com.simprints.id.testtools.TestApplication
import com.simprints.id.testtools.TestTimeHelperImpl
import com.simprints.id.testtools.UnitTestConfig
import com.simprints.infra.config.ConfigManager
import com.simprints.infra.config.domain.models.ProjectConfiguration
import com.simprints.infra.config.domain.models.SynchronizationConfiguration
import com.simprints.infra.config.domain.models.UpSynchronizationConfiguration
import com.simprints.testtools.common.coroutines.TestCoroutineRule
import com.simprints.testtools.common.coroutines.TestDispatcherProvider
import com.simprints.testtools.unit.robolectric.ShadowAndroidXMultiDex
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = TestApplication::class, shadows = [ShadowAndroidXMultiDex::class])
class EventSyncMasterWorkerTest {

    companion object {
        const val UNIQUE_SYNC_ID = "UNIQUE_SYNC_ID"
    }

    private val app = ApplicationProvider.getApplicationContext() as TestApplication

    @get:Rule
    val testCoroutineRule = TestCoroutineRule()
    private val testDispatcherProvider = TestDispatcherProvider(testCoroutineRule)

    private val wm: WorkManager
        get() = WorkManager.getInstance(ApplicationProvider.getApplicationContext())

    private lateinit var masterWorker: EventSyncMasterWorker
    private val synchronizationConfiguration = mockk<SynchronizationConfiguration>()
    private val projectConfiguration = mockk<ProjectConfiguration> {
        every { synchronization } returns synchronizationConfiguration
    }
    private val configManagerMock = mockk<ConfigManager>(relaxed = true) {
        coEvery { getProjectConfiguration() } returns projectConfiguration
    }

    @Before
    fun setUp() {
        UnitTestConfig().setupWorkManager()

        masterWorker = TestListenableWorkerBuilder<EventSyncMasterWorker>(app)
            .setTags(listOf(MASTER_SYNC_SCHEDULER_PERIODIC_TIME))
            .build()

        app.component = mockk(relaxed = true)
        mockDependencies()
    }

    private fun mockDependencies() {
        with(masterWorker) {
            resultSetter = mockk(relaxed = true)
            downSyncWorkerBuilder = mockk(relaxed = true)
            upSyncWorkerBuilder = mockk(relaxed = true)
            eventSyncCache = mockk(relaxed = true)
            eventSyncSubMasterWorkersBuilder = mockk(relaxed = true)
            timeHelper = TestTimeHelperImpl()
            configManager = configManagerMock
            dispatcher = testDispatcherProvider
        }
    }

    @Test
    fun doWork_syncNotGoing_shouldEnqueueANewUniqueSync() = runBlocking {
        val uniqueSyncId = masterWorker.uniqueSyncId
        prepareSyncWorkers(uniqueSyncId)

        masterWorker.doWork()

        assertWorkerOutput(uniqueSyncId)
        assertSyncChainWasBuilt()
        assertAllWorkersAreEnqueued(uniqueSyncId)
    }

    @Test
    fun doWork_syncNotGoingAndBackgroundOff_shouldEnqueueOnlyUpSyncWorkers() = runBlocking {
        with(masterWorker) {
            val uniqueSyncId = masterWorker.uniqueSyncId
            prepareSyncWorkers(uniqueSyncId)
            mockSubjectsDownSyncSetting(SynchronizationConfiguration.Frequency.ONLY_PERIODICALLY_UP_SYNC)

            masterWorker.doWork()

            assertWorkerOutput(uniqueSyncId)
            coVerify(exactly = 0) { downSyncWorkerBuilder.buildDownSyncWorkerChain(any()) }
            coVerify(exactly = 1) { upSyncWorkerBuilder.buildUpSyncWorkerChain(any()) }

            assertSyncWorkersState(uniqueSyncId, ENQUEUED, START_SYNC_REPORTER)
            assertSyncWorkersState(uniqueSyncId, BLOCKED, UP_COUNTER)
            assertSyncWorkersState(uniqueSyncId, BLOCKED, UPLOADER)
            assertSyncWorkersState(uniqueSyncId, BLOCKED, END_SYNC_REPORTER)
            assertTotalNumberOfWorkers(uniqueSyncId, 4)
        }
    }

    @Test
    fun doWork_syncNotGoingAndBackgroundOn_shouldEnqueueAllWorkers() = runBlocking {
        val uniqueSyncId = masterWorker.uniqueSyncId
        prepareSyncWorkers(uniqueSyncId)
        mockSubjectsDownSyncSetting(SynchronizationConfiguration.Frequency.PERIODICALLY)

        masterWorker.doWork()

        assertWorkerOutput(uniqueSyncId)
        assertSyncChainWasBuilt()
        assertAllWorkersAreEnqueued(uniqueSyncId)
    }

    @Test
    fun doWorkAsOneTimeSync_shouldEnqueueAllWorkers() = runBlocking {
        buildOneTimeMasterWorker()
        mockSubjectsDownSyncSetting(SynchronizationConfiguration.Frequency.PERIODICALLY)
        val uniqueSyncId = masterWorker.uniqueSyncId
        prepareSyncWorkers(uniqueSyncId)

        masterWorker.doWork()

        assertWorkerOutput(uniqueSyncId)
        assertSyncChainWasBuilt()
        assertAllWorkersAreEnqueued(uniqueSyncId)
    }

    @Test
    fun doWork_syncGoing_shouldReturnTheExistingUniqueSync() = runBlocking {
        val existingSyncId = enqueueASyncWorker()

        masterWorker.doWork()

        assertWorkerOutput(existingSyncId)
        assertSyncChainWasNotBuild()
    }

    @Test
    fun doWork_errorOccurs_shouldWorkerFail() = runBlocking {
        coEvery { masterWorker.downSyncWorkerBuilder.buildDownSyncWorkerChain(any()) } throws Throwable(
            "IO Error"
        )

        masterWorker.doWork()

        verify { masterWorker.resultSetter.failure(any()) }
    }

    @Test
    fun doWork_cantSyncSimprints() = runBlocking {
        val uniqueSyncId = masterWorker.uniqueSyncId
        prepareSyncWorkers(uniqueSyncId)
        mockSubjectsDownSyncSetting(SynchronizationConfiguration.Frequency.ONLY_PERIODICALLY_UP_SYNC)
        every { synchronizationConfiguration.up } returns UpSynchronizationConfiguration(
            simprints = UpSynchronizationConfiguration.SimprintsUpSynchronizationConfiguration(
                UpSynchronizationConfiguration.UpSynchronizationKind.NONE,
            ),
            coSync = UpSynchronizationConfiguration.CoSyncUpSynchronizationConfiguration(
                UpSynchronizationConfiguration.UpSynchronizationKind.ALL
            )
        )

        masterWorker.doWork()

        verify { masterWorker.resultSetter.success() }
        assertSyncChainWasNotBuild()
    }

    private fun enqueueASyncWorker(): String {
        wm.enqueue(
            OneTimeWorkRequestBuilder<EventDownSyncDownloaderWorker>()
                .setConstraints(constraintsForWorkers())
                .addTag(TAG_SUBJECTS_SYNC_ALL_WORKERS)
                .addTag(TAG_SUBJECTS_DOWN_SYNC_ALL_WORKERS)
                .addTag("${TAG_MASTER_SYNC_ID}$UNIQUE_SYNC_ID")
                .build()
        )
        return UNIQUE_SYNC_ID
    }

    private fun assertAllWorkersAreEnqueued(uniqueSyncId: String) {
        assertSyncWorkersState(uniqueSyncId, ENQUEUED, START_SYNC_REPORTER)
        assertSyncWorkersState(uniqueSyncId, BLOCKED, UP_COUNTER)
        assertSyncWorkersState(uniqueSyncId, BLOCKED, UPLOADER)
        assertSyncWorkersState(uniqueSyncId, BLOCKED, DOWNLOADER)
        assertSyncWorkersState(uniqueSyncId, BLOCKED, DOWN_COUNTER)
        assertSyncWorkersState(uniqueSyncId, BLOCKED, END_SYNC_REPORTER)
        assertTotalNumberOfWorkers(uniqueSyncId, 6)
    }

    private fun assertTotalNumberOfWorkers(uniqueSyncId: String, total: Int) {
        val allWorkers = wm.getWorkInfosByTag("${TAG_MASTER_SYNC_ID}$uniqueSyncId").get()
        assertThat(allWorkers.size).isEqualTo(total)
    }

    private fun assertSyncWorkersState(
        uniqueSyncId: String,
        state: WorkInfo.State,
        specificType: EventSyncWorkerType? = null
    ) {

        val allWorkers = wm.getWorkInfosByTag("${TAG_MASTER_SYNC_ID}$uniqueSyncId").get()
        val specificWorkers =
            specificType?.let { allWorkers.filterByTags(tagForType(specificType)) } ?: allWorkers

        assertThat(specificWorkers.size).isEqualTo(1)
        assertThat(specificWorkers.all { it.state == state }).isTrue()
    }

    private fun constraintsForWorkers() =
        Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

    private fun assertSyncChainWasNotBuild() = assertSyncChainWasBuilt(0)
    private fun assertSyncChainWasBuilt(nTimes: Int = 1) {
        coVerify(exactly = nTimes) { masterWorker.downSyncWorkerBuilder.buildDownSyncWorkerChain(any()) }
        coVerify(exactly = nTimes) { masterWorker.downSyncWorkerBuilder.buildDownSyncWorkerChain(any()) }
    }

    private fun prepareSyncWorkers(uniqueSyncId: String) {
        coEvery { masterWorker.downSyncWorkerBuilder.buildDownSyncWorkerChain(any()) } returns buildDownSyncWorkers(
            uniqueSyncId
        )
        coEvery { masterWorker.upSyncWorkerBuilder.buildUpSyncWorkerChain(any()) } returns buildUpSyncWorkers(
            uniqueSyncId
        )
        coEvery { masterWorker.eventSyncSubMasterWorkersBuilder.buildStartSyncReporterWorker(any()) } returns buildStartSyncReporterWorker(
            uniqueSyncId
        )
        coEvery { masterWorker.eventSyncSubMasterWorkersBuilder.buildEndSyncReporterWorker(any()) } returns buildEndSyncReporterWorker(
            uniqueSyncId
        )

    }

    private fun buildEndSyncReporterWorker(uniqueSyncId: String) =
        OneTimeWorkRequest.Builder(EventEndSyncReporterWorker::class.java)
            .addTagForMasterSyncId(uniqueSyncId)
            .addTagForScheduledAtNow()
            .addCommonTagForAllSyncWorkers()
            .addTagForEndSyncReporter()
            .setInputData(workDataOf(EventEndSyncReporterWorker.SYNC_ID_TO_MARK_AS_COMPLETED to uniqueSyncId))
            .setConstraints(constraintsForWorkers()).build() as OneTimeWorkRequest

    private fun buildStartSyncReporterWorker(uniqueSyncId: String) =
        OneTimeWorkRequest.Builder(EventStartSyncReporterWorker::class.java)
            .addTagForMasterSyncId(uniqueSyncId)
            .addTagForScheduledAtNow()
            .addCommonTagForAllSyncWorkers()
            .addTagForStartSyncReporter()
            .setInputData(workDataOf(EventStartSyncReporterWorker.SYNC_ID_STARTED to uniqueSyncId))
            .setConstraints(constraintsForWorkers()).build() as OneTimeWorkRequest

    private fun buildDownSyncWorkers(uniqueSyncId: String): List<OneTimeWorkRequest> =
        listOf(
            OneTimeWorkRequestBuilder<EventDownSyncDownloaderWorker>()
                .setConstraints(constraintsForWorkers())
                .addTag("${TAG_MASTER_SYNC_ID}$uniqueSyncId")
                .addTag(TAG_SUBJECTS_SYNC_ALL_WORKERS)
                .addTag(TAG_SUBJECTS_DOWN_SYNC_ALL_WORKERS)
                .addTag(tagForType(DOWNLOADER))
                .build(),
            OneTimeWorkRequestBuilder<EventDownSyncCountWorker>()
                .setConstraints(constraintsForWorkers())
                .addTag("${TAG_MASTER_SYNC_ID}$uniqueSyncId")
                .addTag(TAG_SUBJECTS_SYNC_ALL_WORKERS)
                .addTag(TAG_SUBJECTS_DOWN_SYNC_ALL_WORKERS)
                .addTag(tagForType(DOWN_COUNTER))
                .build()
        )

    private fun buildUpSyncWorkers(uniqueSyncId: String): List<OneTimeWorkRequest> =
        listOf(
            OneTimeWorkRequestBuilder<EventUpSyncUploaderWorker>()
                .setConstraints(constraintsForWorkers())
                .addTag("${TAG_MASTER_SYNC_ID}$uniqueSyncId")
                .addTag(TAG_SUBJECTS_SYNC_ALL_WORKERS)
                .addTag(TAG_SUBJECTS_UP_SYNC_ALL_WORKERS)
                .addTag(tagForType(UPLOADER))
                .build(),
            OneTimeWorkRequestBuilder<EventUpSyncCountWorker>()
                .setConstraints(constraintsForWorkers())
                .addTag("${TAG_MASTER_SYNC_ID}$uniqueSyncId")
                .addTag(TAG_SUBJECTS_SYNC_ALL_WORKERS)
                .addTag(TAG_SUBJECTS_DOWN_SYNC_ALL_WORKERS)
                .addTag(tagForType(UP_COUNTER))
                .build()
        )

    private fun assertWorkerOutput(uniqueSyncId: String) {
        verify { masterWorker.resultSetter.success(workDataOf(OUTPUT_LAST_SYNC_ID to uniqueSyncId)) }
    }

    private fun mockSubjectsDownSyncSetting(frequency: SynchronizationConfiguration.Frequency) {
        every { synchronizationConfiguration.frequency } returns frequency
    }

    private fun buildOneTimeMasterWorker() {
        masterWorker = TestListenableWorkerBuilder<EventSyncMasterWorker>(app)
            .setTags(listOf(MASTER_SYNC_SCHEDULER_ONE_TIME))
            .build()
        mockDependencies()
    }
}
