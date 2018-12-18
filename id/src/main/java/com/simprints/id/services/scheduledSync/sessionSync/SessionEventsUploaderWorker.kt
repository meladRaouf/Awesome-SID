package com.simprints.id.services.scheduledSync.sessionSync

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.simprints.id.Application
import com.simprints.id.data.analytics.AnalyticsManager
import com.simprints.id.data.analytics.eventData.controllers.domain.SessionEventsManager
import com.simprints.id.data.db.remote.RemoteDbManager
import com.simprints.id.data.loginInfo.LoginInfoManager
import com.simprints.id.exceptions.unsafe.WorkerInjectionFailedError
import com.simprints.id.tools.TimeHelper
import timber.log.Timber
import javax.inject.Inject
import androidx.work.Result

class SessionEventsUploaderWorker(context: Context, params: WorkerParameters)
    : Worker(context, params) {

    @Inject
    lateinit var loginInfoManager: LoginInfoManager

    @Inject
    lateinit var sessionEventsManager: SessionEventsManager

    @Inject
    lateinit var analyticsManager: AnalyticsManager

    @Inject
    lateinit var timeHelper: TimeHelper

    @Inject
    lateinit var remoteDbManager: RemoteDbManager

    private val sessionsApiClient by lazy {
        remoteDbManager.getSessionsApiClient().blockingGet()
    }

    private val sessionIdsToUpload by lazy {
        inputData.getStringArray(SessionEventsSyncMasterTask.SESSIONS_IDS_KEY)?.toList()
            ?: throw IllegalArgumentException("Sessions ids required")
    }

    private val signedProjectId by lazy {
        inputData.getString(SessionEventsSyncMasterTask.PROJECT_ID_KEY)
            ?: throw IllegalArgumentException("Project Id required")
    }

    override fun doWork(): Result {
        Timber.d("SessionEventsUploaderWorker doWork()")
        injectDependencies()

        return try {
            val task = SessionEventsUploaderTask(
                signedProjectId,
                sessionIdsToUpload,
                sessionEventsManager,
                timeHelper,
                sessionsApiClient)

            task.execute().blockingAwait()
            Timber.d("SessionEventsUploaderWorker done()")

            Result.success()
        } catch (throwable: Throwable) {
            Timber.d("SessionEventsUploaderWorker error()")

            Timber.e(throwable)
            analyticsManager.logThrowable(throwable)
            Result.failure()
        }
    }

    private fun injectDependencies() {
        val context = applicationContext
        if (context is Application) {
            context.component.inject(this)
        } else {
            throw WorkerInjectionFailedError.forWorker<SessionEventsUploaderWorker>()
        }
    }
}
