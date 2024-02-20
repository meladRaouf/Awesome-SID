package com.simprints.infra.sync

import androidx.work.WorkManager
import com.simprints.infra.authstore.AuthStore
import com.simprints.infra.sync.extensions.schedulePeriodicWorker
import com.simprints.infra.sync.extensions.startWorker
import com.simprints.infra.sync.config.worker.DeviceConfigDownSyncWorker
import com.simprints.infra.sync.config.worker.ProjectConfigDownSyncWorker
import com.simprints.infra.sync.usecase.CleanupDeprecatedWorkersUseCase
import javax.inject.Inject

internal class SyncOrchestratorImpl @Inject constructor(
    private val workManager: WorkManager,
    private val authStore: AuthStore,
    private val cleanupDeprecatedWorkers: CleanupDeprecatedWorkersUseCase,
) : SyncOrchestrator {

    override suspend fun scheduleBackgroundWork() {
        if (authStore.signedInProjectId.isNotEmpty()) {
            workManager.schedulePeriodicWorker<ProjectConfigDownSyncWorker>(
                SyncConstants.PROJECT_SYNC_WORK_NAME,
                SyncConstants.PROJECT_SYNC_REPEAT_INTERVAL
            )
            workManager.schedulePeriodicWorker<DeviceConfigDownSyncWorker>(
                SyncConstants.DEVICE_SYNC_WORK_NAME,
                SyncConstants.DEVICE_SYNC_REPEAT_INTERVAL
            )
            // TODO eventSyncManager.scheduleSync()
            // TODO imageUpSyncScheduler.scheduleImageUpSync()
            // TODO firmwareFileUpdateScheduler.scheduleOrCancelWorkIfNecessary()
        }
    }

    override suspend fun cancelBackgroundWork() {
        workManager.cancelUniqueWork(SyncConstants.PROJECT_SYNC_WORK_NAME)
        workManager.cancelUniqueWork(SyncConstants.DEVICE_SYNC_WORK_NAME)
    }

    override fun startDeviceSync() {
        workManager.startWorker<DeviceConfigDownSyncWorker>(SyncConstants.DEVICE_SYNC_WORK_NAME_ONE_TIME)
    }


    override fun cleanupWorkers() {
        cleanupDeprecatedWorkers()
    }
}
