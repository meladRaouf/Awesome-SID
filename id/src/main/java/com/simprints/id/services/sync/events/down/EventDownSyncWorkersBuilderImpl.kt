package com.simprints.id.services.sync.events.down

import androidx.work.*
import com.simprints.core.domain.modality.toMode
import com.simprints.core.tools.json.JsonHelper
import com.simprints.eventsystem.events_sync.down.EventDownSyncScopeRepository
import com.simprints.eventsystem.events_sync.down.domain.EventDownSyncOperation
import com.simprints.eventsystem.events_sync.down.domain.EventDownSyncScope
import com.simprints.id.data.prefs.IdPreferencesManager
import com.simprints.id.services.sync.events.common.*
import com.simprints.id.services.sync.events.down.workers.EventDownSyncCountWorker
import com.simprints.id.services.sync.events.down.workers.EventDownSyncCountWorker.Companion.INPUT_COUNT_WORKER_DOWN
import com.simprints.id.services.sync.events.down.workers.EventDownSyncDownloaderWorker
import com.simprints.id.services.sync.events.down.workers.EventDownSyncDownloaderWorker.Companion.INPUT_DOWN_SYNC_OPS
import com.simprints.id.services.sync.events.master.workers.EventSyncMasterWorker.Companion.MIN_BACKOFF_SECS
import java.util.*
import java.util.concurrent.TimeUnit

class EventDownSyncWorkersBuilderImpl(
    private val downSyncScopeRepository: EventDownSyncScopeRepository,
    private val jsonHelper: JsonHelper,
    private val preferencesManager: IdPreferencesManager
) : EventDownSyncWorkersBuilder {

    override suspend fun buildDownSyncWorkerChain(uniqueSyncId: String?): List<OneTimeWorkRequest> {
        val downSyncScope = downSyncScopeRepository.getDownSyncScope(
            preferencesManager.modalities.map { it.toMode() },
            preferencesManager.selectedModules.toList(),
            preferencesManager.syncGroup
        )

        val uniqueDownSyncId = UUID.randomUUID().toString()
        val workerBuilders = downSyncScope.operations.map {
            getDownSyncWorkerBuilder(uniqueSyncId, uniqueDownSyncId, it)
        } + buildCountWorker(uniqueSyncId, uniqueDownSyncId, downSyncScope)

        return workerBuilders.map { it.build() }
    }

    override suspend fun buildNewModulesDownSyncWorkerChain(uniqueSyncId: String?): List<OneTimeWorkRequest>? {
        if (!preferencesManager.newlyAddedModules.isEmpty()) {
            val downSyncScope = downSyncScopeRepository.getNewModulesDownSyncScope(
                preferencesManager.modalities.map { it.toMode() },
                preferencesManager.newlyAddedModules.toList()
            )

            val uniqueDownSyncId = UUID.randomUUID().toString()
            val workerBuilders = downSyncScope.operations.map {
                getDownSyncWorkerBuilder(uniqueSyncId, uniqueDownSyncId, it)
                    .addCommonTagForNewModulesDownloaders() as OneTimeWorkRequest.Builder
            } + buildCountWorker(uniqueSyncId, uniqueDownSyncId, downSyncScope)
                .addCommonTagForNewModulesDownloaders() as OneTimeWorkRequest.Builder

            return workerBuilders.map { it.build() }
        } else {
            return null
        }
    }

    private fun getDownSyncWorkerBuilder(
        uniqueSyncID: String?,
        uniqueDownSyncID: String,
        downSyncOperation: EventDownSyncOperation
    ): OneTimeWorkRequest.Builder =
        OneTimeWorkRequest.Builder(EventDownSyncDownloaderWorker::class.java)
            .setInputData(workDataOf(INPUT_DOWN_SYNC_OPS to jsonHelper.toJson(downSyncOperation)))
            .setDownSyncWorker(uniqueSyncID, uniqueDownSyncID, getDownSyncWorkerConstraints())
            .addCommonTagForDownloaders() as OneTimeWorkRequest.Builder

    private fun buildCountWorker(
        uniqueSyncID: String?,
        uniqueDownSyncID: String,
        eventDownSyncScope: EventDownSyncScope
    ): OneTimeWorkRequest.Builder =
        OneTimeWorkRequest.Builder(EventDownSyncCountWorker::class.java)
            .setInputData(workDataOf(INPUT_COUNT_WORKER_DOWN to jsonHelper.toJson(eventDownSyncScope)))
            .setDownSyncWorker(uniqueSyncID, uniqueDownSyncID, getDownSyncWorkerConstraints())
            .addCommonTagForDownCounters() as OneTimeWorkRequest.Builder

    private fun getDownSyncWorkerConstraints() =
        Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

    private fun WorkRequest.Builder<*, *>.setDownSyncWorker(
        uniqueMasterSyncId: String?,
        uniqueDownMasterSyncId: String,
        constraints: Constraints
    ) =
        this.setConstraints(constraints)
            .addTagForMasterSyncId(uniqueMasterSyncId)
            .addTagForDownSyncId(uniqueDownMasterSyncId)
            .addTagForScheduledAtNow()
            .addCommonTagForDownWorkers()
            .addCommonTagForAllSyncWorkers()
            .setBackoffCriteria(BackoffPolicy.LINEAR, MIN_BACKOFF_SECS, TimeUnit.SECONDS)
}
