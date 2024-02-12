package com.simprints.core.workers

import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.simprints.core.ExcludedFromGeneratedTestCoverageReports
import com.simprints.infra.logging.LoggingConstants.CrashReportTag
import com.simprints.infra.logging.Simber
import com.simprints.infra.network.exceptions.NetworkConnectionException
import com.simprints.infra.resources.R
import kotlinx.coroutines.CancellationException

@ExcludedFromGeneratedTestCoverageReports("Abstract base class")
abstract class SimCoroutineWorker(
    private val context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    abstract val tag: String
    private var resultSetter = WorkerResultSetter()

    protected fun retry(t: Throwable? = null, message: String = t?.message ?: ""): Result {
        crashlyticsLog("[Retry] $message")

        logExceptionIfRequired(t)
        return resultSetter.retry()
    }

    protected fun fail(
        t: Throwable,
        message: String? = t.message ?: "",
        outputData: Data? = null
    ): Result {

        crashlyticsLog("[Failed] $message")
        logExceptionIfRequired(t)
        return resultSetter.failure(outputData)

    }

    protected fun success(
        outputData: Data? = null,
        message: String = ""
    ): Result {
        crashlyticsLog("[Success] $message")

        return resultSetter.success(outputData)
    }

    protected suspend fun showProgressNotification() {
        try {
            setForeground(getForegroundInfo())
        } catch (setForegroundException: Throwable) {
            // Setting foreground (showing the notification) may be restricted by the system
            // in new Android versions, when the app isn't on screen, battery optimization on, etc.;
            // see https://developer.android.com/develop/background-work/services/foreground-services#bg-access-restrictions.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && setForegroundException is ForegroundServiceStartNotAllowedException
            ) {
                Simber.i(setForegroundException, "Worker notification service restricted")
            } else {
                Simber.e(setForegroundException)
            }
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                WORKER_FOREGROUND_NOTIFICATION_CHANNEL_ID,
                WORKER_FOREGROUND_NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW,
            )
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(
            applicationContext,
            WORKER_FOREGROUND_NOTIFICATION_CHANNEL_ID,
        )
            .setContentTitle(context.getString(R.string.notification_sync_title))
            .setContentText(context.getString(R.string.notification_sync_description))
            .setSmallIcon(R.drawable.ic_notification_sync)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    foregroundServiceBehavior = Notification.FOREGROUND_SERVICE_DEFERRED
                }
            }
            .build()
        return ForegroundInfo(WORKER_FOREGROUND_NOTIFICATION_ID, notification)
    }

    protected fun crashlyticsLog(message: String) {
        Simber.tag(CrashReportTag.SYNC.name).i("$tag - $message")
    }

    private fun logExceptionIfRequired(t: Throwable?) {
        t?.let {
            when (t) {
                is CancellationException -> Simber.d(t)
                // Record network issues only in Analytics
                is NetworkConnectionException -> Simber.i(t)
                else -> Simber.e(t)
            }
        }
    }

    private companion object {
        private const val WORKER_FOREGROUND_NOTIFICATION_ID = 2
        private const val WORKER_FOREGROUND_NOTIFICATION_CHANNEL_ID =
            "WORKER_FOREGROUND_NOTIFICATION"
        private const val WORKER_FOREGROUND_NOTIFICATION_CHANNEL_NAME = "Sync in progress"
    }
}
