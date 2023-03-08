package com.simprints.id.services.sync.events.up.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.WorkInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.module.kotlin.MissingKotlinParameterException
import com.simprints.core.DispatcherBG
import com.simprints.core.tools.json.JsonHelper
import com.simprints.core.workers.SimCoroutineWorker
import com.simprints.id.data.db.events_sync.up.domain.old.toNewScope
import com.simprints.id.exceptions.unexpected.MalformedSyncOperationException
import com.simprints.id.services.sync.events.up.workers.EventUpSyncUploaderWorker.Companion.OUTPUT_UP_SYNC
import com.simprints.id.services.sync.events.up.workers.EventUpSyncUploaderWorker.Companion.PROGRESS_UP_SYNC
import com.simprints.infra.eventsync.status.up.domain.EventUpSyncScope
import com.simprints.infra.eventsync.sync.common.*
import com.simprints.infra.eventsync.sync.up.EventUpSyncHelper
import com.simprints.infra.logging.Simber
import com.simprints.infra.login.LoginManager
import com.simprints.infra.network.exceptions.BackendMaintenanceException
import com.simprints.infra.network.exceptions.SyncCloudIntegrationException
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import com.simprints.id.data.db.events_sync.up.domain.old.EventUpSyncScope as OldEventUpSyncScope

@HiltWorker
class EventUpSyncUploaderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val upSyncHelper: EventUpSyncHelper,
    private val eventSyncCache: EventSyncCache,
    private val loginManager: LoginManager,
    @DispatcherBG private val dispatcher: CoroutineDispatcher,
) : SimCoroutineWorker(context, params), WorkerProgressCountReporter {

    override val tag: String = EventUpSyncUploaderWorker::class.java.simpleName

    private val upSyncScope by lazy {
        try {
            val jsonInput = inputData.getString(INPUT_UP_SYNC)
                ?: throw IllegalArgumentException("input required")
            Simber.d("Received $jsonInput")
            parseUpSyncInput(jsonInput)
        } catch (t: Throwable) {
            if (t is JsonParseException || t is JsonMappingException) {
                EventUpSyncScope.ProjectScope(loginManager.getSignedInProjectIdOrEmpty())
            } else {
                throw MalformedSyncOperationException(t.message ?: "")
            }
        }
    }

    override suspend fun doWork(): Result =
        withContext(dispatcher) {
            try {
                Simber.tag(SYNC_LOG_TAG).d("[UPLOADER] Started")

                val workerId = this@EventUpSyncUploaderWorker.id.toString()
                var count = eventSyncCache.readProgress(workerId)

                crashlyticsLog("Start")
                upSyncHelper.upSync(this, upSyncScope.operation).collect {
                    count += it.progress
                    eventSyncCache.saveProgress(workerId, count)
                    Simber.tag(SYNC_LOG_TAG).d("[UPLOADER] Uploaded $count for batch : $it")

                    reportCount(count)
                }

                Simber.tag(SYNC_LOG_TAG).d("[UPLOADER] Done")
                success(workDataOf(OUTPUT_UP_SYNC to count), "Total uploaded: $count")
            } catch (t: Throwable) {
                Simber.d(t)
                Simber.tag(SYNC_LOG_TAG).d("[UPLOADER] Failed ${t.message}")
                retryOrFailIfCloudIntegrationOrBackendMaintenanceError(t)
            }
        }

    private fun retryOrFailIfCloudIntegrationOrBackendMaintenanceError(t: Throwable): Result {
        return when (t) {
            is BackendMaintenanceException -> {
                fail(
                    t,
                    t.message,
                    workDataOf(
                        OUTPUT_FAILED_BECAUSE_BACKEND_MAINTENANCE to true,
                        OUTPUT_ESTIMATED_MAINTENANCE_TIME to t.estimatedOutage
                    )
                )
            }
            is SyncCloudIntegrationException -> {
                fail(t, t.message, workDataOf(OUTPUT_FAILED_BECAUSE_CLOUD_INTEGRATION to true))
            }
            else -> {
                retry(t)
            }
        }
    }

    override suspend fun reportCount(count: Int) {
        setProgress(workDataOf(PROGRESS_UP_SYNC to count))
    }

    companion object {
        const val INPUT_UP_SYNC = "INPUT_UP_SYNC"
        const val PROGRESS_UP_SYNC = "PROGRESS_UP_SYNC"
        const val OUTPUT_UP_SYNC = "OUTPUT_UP_SYNC"


        // TODO throw this away... thank you
        fun parseUpSyncInput(input: String): EventUpSyncScope {
            return try {
                JsonHelper.fromJson(input)
            } catch (ex: MissingKotlinParameterException) {
                val result = JsonHelper.fromJson<OldEventUpSyncScope>(input)
                result.toNewScope()
            }
        }
    }
}

suspend fun WorkInfo.extractUpSyncProgress(eventSyncCache: EventSyncCache): Int {
    val progress = this.progress.getInt(PROGRESS_UP_SYNC, -1)
    val output = this.outputData.getInt(OUTPUT_UP_SYNC, -1)

    //When the worker is not running (e.g. ENQUEUED due to errors), the output and progress are cleaned.
    val cached = eventSyncCache.readProgress(id.toString())
    return maxOf(progress, output, cached)
}
