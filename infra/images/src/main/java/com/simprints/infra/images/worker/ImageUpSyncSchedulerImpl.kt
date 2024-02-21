package com.simprints.infra.images.worker

import android.content.Context
import androidx.work.*
import androidx.work.WorkRequest.Companion.MIN_BACKOFF_MILLIS
import com.simprints.infra.config.store.ConfigRepository
import com.simprints.infra.config.store.models.imagesUploadRequiresUnmeteredConnection
import com.simprints.infra.images.BuildConfig
import com.simprints.infra.images.ImageUpSyncScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject

internal class ImageUpSyncSchedulerImpl @Inject constructor(
    @ApplicationContext context: Context,
    private val configRepo: ConfigRepository,
) : ImageUpSyncScheduler {

    private val workManager = WorkManager.getInstance(context)

    override suspend fun scheduleImageUpSync() {
        workManager.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            buildWork()
        )
    }

    override suspend fun rescheduleImageUpSync() {
        workManager.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
            buildWork()
        )
    }

    override fun cancelImageUpSync() {
        workManager.cancelUniqueWork(WORK_NAME)
    }

    private suspend fun buildWork(): PeriodicWorkRequest {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(
                if (configRepo.getProjectConfiguration().imagesUploadRequiresUnmeteredConnection()) NetworkType.UNMETERED
                else NetworkType.CONNECTED
            )
            .build()

        return PeriodicWorkRequestBuilder<ImageUpSyncWorker>(
            SYNC_REPEAT_INTERVAL,
            SYNC_REPEAT_UNIT
        )
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            ).build()
    }

    companion object {

        private const val WORK_NAME = "image-upsync-work-v2"
        private const val SYNC_REPEAT_INTERVAL =
            BuildConfig.SYNC_PERIODIC_WORKER_INTERVAL_MINUTES
        private val SYNC_REPEAT_UNIT = TimeUnit.MINUTES
    }
}
