package com.simprints.id.services.sync.images.up

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.WorkerParameters
import com.simprints.core.DispatcherIO
import com.simprints.id.services.sync.events.common.SimCoroutineWorker
import com.simprints.infra.images.ImageRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

@HiltWorker
class ImageUpSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val imageRepository: ImageRepository,
    @DispatcherIO private val dispatcher: CoroutineDispatcher
) : SimCoroutineWorker(context, params) {

    override val tag: String = ImageUpSyncWorker::class.java.simpleName

    override suspend fun doWork(): Result =
        withContext(dispatcher) {
            crashlyticsLog("Start")

            try {
                if (imageRepository.uploadStoredImagesAndDelete()) {
                    success()
                } else {
                    retry()
                }
            } catch (ex: Exception) {
                retry(t = ex)
            }
        }
}
