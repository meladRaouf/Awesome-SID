package com.simprints.infra.sync.extensions

import androidx.work.*
import com.simprints.infra.sync.SyncConstants


internal fun defaultWorkerConstraints() = Constraints.Builder()
    .setRequiredNetworkType(NetworkType.CONNECTED)
    .build()

internal inline fun <reified T : ListenableWorker> WorkManager.schedulePeriodicWorker(
    workName: String,
    repeatInterval: Long,
    existingWorkPolicy: ExistingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.UPDATE,
    initialDelay: Long = 0,
    backoffInterval: Long = SyncConstants.DEFAULT_BACKOFF_INTERVAL_MINUTES,
    constraints: Constraints = defaultWorkerConstraints(),
    tags: List<String> = emptyList(),
    inputData: Data? = null,
) = enqueueUniquePeriodicWork(
    workName,
    existingWorkPolicy,
    PeriodicWorkRequestBuilder<T>(repeatInterval, SyncConstants.SYNC_TIME_UNIT)
        .setConstraints(constraints)
        .setInitialDelay(initialDelay, SyncConstants.SYNC_TIME_UNIT)
        .setBackoffCriteria(BackoffPolicy.LINEAR, backoffInterval, SyncConstants.SYNC_TIME_UNIT)
        .let { if (inputData != null) it.setInputData(inputData) else it }
        .let { tags.fold(it) { builder, tag -> builder.addTag(tag) } }
        .build()
)

internal inline fun <reified T : ListenableWorker> WorkManager.startWorker(
    workName: String,
    existingWorkPolicy: ExistingWorkPolicy = ExistingWorkPolicy.KEEP,
    initialDelay: Long = 0,
    backoffInterval: Long = SyncConstants.DEFAULT_BACKOFF_INTERVAL_MINUTES,
    constraints: Constraints = defaultWorkerConstraints(),
    tags: List<String> = emptyList(),
    inputData: Data? = null,
) = this.enqueueUniqueWork(
    workName,
    existingWorkPolicy,
    OneTimeWorkRequestBuilder<T>()
        .setConstraints(constraints)
        .setInitialDelay(initialDelay, SyncConstants.SYNC_TIME_UNIT)
        .setBackoffCriteria(BackoffPolicy.LINEAR, backoffInterval, SyncConstants.SYNC_TIME_UNIT)
        .let { if (inputData != null) it.setInputData(inputData) else it }
        .let { tags.fold(it) { builder, tag -> builder.addTag(tag) } }
        .build()
)

internal fun WorkManager.cancelWorkers(vararg workNames: String) {
    workNames.forEach(this::cancelUniqueWork)
}

internal fun List<WorkInfo>.anyRunning() = any { it.state == WorkInfo.State.RUNNING }
