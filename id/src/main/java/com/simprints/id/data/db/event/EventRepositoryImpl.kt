package com.simprints.id.data.db.event

import android.os.Build
import android.os.Build.VERSION
import androidx.annotation.VisibleForTesting
import com.simprints.id.data.analytics.crashreport.CrashReportManager
import com.simprints.id.data.db.event.domain.EventCount
import com.simprints.id.data.db.event.domain.models.*
import com.simprints.id.data.db.event.domain.models.ArtificialTerminationEvent.ArtificialTerminationPayload.Reason
import com.simprints.id.data.db.event.domain.models.ArtificialTerminationEvent.ArtificialTerminationPayload.Reason.NEW_SESSION
import com.simprints.id.data.db.event.domain.models.EventType.SESSION_CAPTURE
import com.simprints.id.data.db.event.domain.models.session.DatabaseInfo
import com.simprints.id.data.db.event.domain.models.session.Device
import com.simprints.id.data.db.event.domain.models.session.SessionCaptureEvent
import com.simprints.id.data.db.event.domain.validators.SessionEventValidatorsFactory
import com.simprints.id.data.db.event.local.EventLocalDataSource
import com.simprints.id.data.db.event.local.SessionDataCache
import com.simprints.id.data.db.event.remote.EventRemoteDataSource
import com.simprints.id.data.db.events_sync.down.domain.RemoteEventQuery
import com.simprints.id.data.db.events_sync.down.domain.fromDomainToApi
import com.simprints.id.data.loginInfo.LoginInfoManager
import com.simprints.id.data.prefs.PreferencesManager
import com.simprints.id.domain.modality.toMode
import com.simprints.id.exceptions.safe.sync.TryToUploadEventsForNotSignedProject
import com.simprints.id.services.sync.events.common.SYNC_LOG_TAG
import com.simprints.id.tools.extensions.bufferedChunks
import com.simprints.id.tools.extensions.isClientAndCloudIntegrationIssue
import com.simprints.id.tools.time.TimeHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.*
import timber.log.Timber
import java.util.*

open class EventRepositoryImpl(
    private val deviceId: String,
    private val appVersionName: String,
    private val loginInfoManager: LoginInfoManager,
    private val eventLocalDataSource: EventLocalDataSource,
    private val eventRemoteDataSource: EventRemoteDataSource,
    private val preferencesManager: PreferencesManager,
    private val crashReportManager: CrashReportManager,
    private val timeHelper: TimeHelper,
    validatorsFactory: SessionEventValidatorsFactory,
    override val libSimprintsVersionName: String,
    private val sessionDataCache: SessionDataCache
) : EventRepository {

    companion object {
        const val PROJECT_ID_FOR_NOT_SIGNED_IN = "NOT_SIGNED_IN"
        const val SESSION_BATCH_SIZE = 20
    }

    private val validators = validatorsFactory.build()

    private val currentProject: String
        get() = if (loginInfoManager.getSignedInProjectIdOrEmpty().isEmpty()) {
            PROJECT_ID_FOR_NOT_SIGNED_IN
        } else {
            loginInfoManager.getSignedInProjectIdOrEmpty()
        }

    override suspend fun createSession(): SessionCaptureEvent {
        closeAllSessions(NEW_SESSION)

        return reportException {
            val sessionCount = eventLocalDataSource.count(type = SESSION_CAPTURE)
            val sessionCaptureEvent = SessionCaptureEvent(
                id = UUID.randomUUID().toString(),
                projectId = currentProject,
                createdAt = timeHelper.now(),
                modalities = preferencesManager.modalities.map { it.toMode() },
                appVersionName = appVersionName,
                libVersionName = libSimprintsVersionName,
                language = preferencesManager.language,
                device = Device(
                    VERSION.SDK_INT.toString(),
                    Build.MANUFACTURER + "_" + Build.MODEL,
                    deviceId
                ),
                databaseInfo = DatabaseInfo(sessionCount)
            )

            saveEvent(sessionCaptureEvent, sessionCaptureEvent)
            sessionDataCache.eventCache[sessionCaptureEvent.id] = sessionCaptureEvent

//            for (i in 1..200) {
//                saveEvent(
//                    ConsentEvent(
//                        timeHelper.now(),
//                        timeHelper.now(),
//                        ConsentEvent.ConsentPayload.Type.INDIVIDUAL,
//                        ConsentEvent.ConsentPayload.Result.ACCEPTED
//                    ), sessionCaptureEvent
//                )
//            }
//
//            for (i in 1..50) {
//                val sessionCaptureEvent2 = SessionCaptureEvent(
//                    id = UUID.randomUUID().toString(),
//                    projectId = currentProject,
//                    createdAt = timeHelper.now(),
//                    modalities = preferencesManager.modalities.map { it.toMode() },
//                    appVersionName = appVersionName,
//                    libVersionName = libSimprintsVersionName,
//                    language = preferencesManager.language,
//                    device = Device(
//                        VERSION.SDK_INT.toString(),
//                        Build.MANUFACTURER + "_" + Build.MODEL,
//                        deviceId
//                    ),
//                    databaseInfo = DatabaseInfo(sessionCount)
//                )
//                saveEvent(sessionCaptureEvent2, sessionCaptureEvent2)
//            }

            sessionCaptureEvent
        }
    }

    override suspend fun addOrUpdateEvent(event: Event) {
        val startTime = System.currentTimeMillis()

        reportException {
            val session = getCurrentCaptureSessionEvent()

            validators.forEach {
                it.validate(sessionDataCache.eventCache.values.toList(), event)
            }

            sessionDataCache.eventCache[event.id] = event

            saveEvent(event, session)
        }

        val endTime = System.currentTimeMillis()
        Timber.v("Save event: ${event.type} = ${endTime - startTime}ms")
    }

    private suspend fun saveEvent(event: Event, session: SessionCaptureEvent) {
        checkAndUpdateLabels(event, session)
        eventLocalDataSource.insertOrUpdate(event)
    }

    private fun checkAndUpdateLabels(event: Event, session: SessionCaptureEvent) {
        event.labels = event.labels.copy(
            sessionId = session.id,
            projectId = session.payload.projectId
        )

        if (event.type.isNotASubjectEvent()) {
            event.labels = event.labels.copy(deviceId = deviceId)
        }
    }

    override suspend fun localCount(projectId: String): Int =
        eventLocalDataSource.count(projectId = projectId)

    override suspend fun localCount(projectId: String, type: EventType): Int =
        eventLocalDataSource.count(projectId = projectId, type = type)

    override suspend fun countEventsToDownload(query: RemoteEventQuery): List<EventCount> =
        eventRemoteDataSource.count(query.fromDomainToApi())

    override suspend fun downloadEvents(
        scope: CoroutineScope,
        query: RemoteEventQuery
    ): ReceiveChannel<Event> =
        eventRemoteDataSource.getEvents(query.fromDomainToApi(), scope)

    @ExperimentalCoroutinesApi
    @FlowPreview
    override suspend fun uploadEvents(projectId: String): Flow<Int> = flow {
        Timber.tag(SYNC_LOG_TAG).d("[EVENT_REPO] Uploading")

        if (projectId != loginInfoManager.getSignedInProjectIdOrEmpty()) {
            throw TryToUploadEventsForNotSignedProject("Only events for the signed in project can be uploaded").also {
                crashReportManager.logException(it)
            }
        }

        Timber.tag(SYNC_LOG_TAG).d("[EVENT_REPO] Uploading batches")
        val batches = createBatches(projectId)

        batches.collect { events ->
            Timber.tag(SYNC_LOG_TAG).d("[EVENT_REPO] Uploading ${events.size} events in a batch")

            try {
                uploadEvents(events, projectId)
                deleteEventsFromDb(events.map { it.id })
            } catch (t: Throwable) {
                Timber.d(t)
                if (t.isClientAndCloudIntegrationIssue()) {
                    crashReportManager.logException(t)
                    // We do not delete subject events (pokedex) since they are important.
                    deleteEventsFromDb(events.filter { it.type.isNotASubjectEvent() }.map { it.id })
                }
            }
            this.emit(events.size)
        }
    }

    override suspend fun deleteSessionEvents(sessionId: String) {
        reportException {
            eventLocalDataSource.deleteAllFromSession(sessionId = sessionId)
        }
    }

    private suspend fun uploadEvents(events: List<Event>, projectId: String) {
        events.filterIsInstance<SessionCaptureEvent>().forEach {
            it.payload.uploadedAt = timeHelper.now()
        }
        eventRemoteDataSource.post(projectId, events)
    }

    private suspend fun deleteEventsFromDb(eventsIds: List<String>) {
        eventsIds.forEach {
            Timber.tag(SYNC_LOG_TAG).d("[EVENT_REPO] Deleting $it")
            eventLocalDataSource.delete(id = it)
        }
    }

    @ExperimentalCoroutinesApi
    @FlowPreview
    @VisibleForTesting
    suspend fun createBatches(projectId: String): Flow<List<Event>> {
        Timber.tag(SYNC_LOG_TAG).d("[EVENT_REPO] Creating batches")
        return flowOf(
            createBatchesForEventsNotInSessions(projectId),
            createBatchesForEventsInSessions(projectId)
        ).flattenConcat()
    }

    @ExperimentalCoroutinesApi
    @Deprecated(
        "Before 2021.1.0, SID could have events not associated with a session in the db like " +
            "EnrolmentRecordCreationEvent that need to be uploaded. After 2021.1.0, SID doesn't generate " +
            "EnrolmentRecordCreationEvent anymore during an enrolment and the event is used only for the down-sync " +
            "(transformed to a subject). So this logic to batch the 'not-related with a session' events is unnecessary " +
            "from 2021.1.0, but it's still required during the migration from previous app versions since the DB may " +
            "still have EnrolmentRecordCreationEvents in the db to upload. Once all devices are on 2021.1.0+, this logic" +
            "can be deleted."
    )
    private suspend fun createBatchesForEventsNotInSessions(projectId: String): Flow<List<Event>> {
        Timber.tag(SYNC_LOG_TAG).d("[EVENT_REPO] Record events to upload")

        val events = eventLocalDataSource
            .loadAllFromProject(projectId = projectId)
            .filter { it.labels.sessionId == null }
            .bufferedChunks(SESSION_BATCH_SIZE)

        return events.map { it.toList() }
    }

    /**
     * Each session will create its own Batch. Sessions have between 10 and 20 events, which renders [SESSION_BATCH_SIZE]
     * pretty much useless, as no 2 sessions will ever be merged. Because of that, it means that each session will
     * be uploaded individually to BFSID.
     */
    private suspend fun createBatchesForEventsInSessions(projectId: String): Flow<List<Event>> {
        // We don't upload unsigned sessions because the back-end would reject them.
        val sessionsToUpload: Flow<SessionCaptureEvent> = loadSessions(true)
            .filter { it.labels.projectId == projectId }
            .filter { it.labels.projectId != PROJECT_ID_FOR_NOT_SIGNED_IN }

        Timber.tag(SYNC_LOG_TAG).d("[EVENT_REPO] Sessions to upload ${sessionsToUpload.count()}")

        return sessionsToUpload.map { session ->
            eventLocalDataSource.loadAllFromSession(sessionId = session.id).toList()
        }
    }

    override suspend fun getCurrentCaptureSessionEvent(): SessionCaptureEvent = reportException {
        sessionDataCache.eventCache.values.toList().filterIsInstance<SessionCaptureEvent>()
            .firstOrNull()
            ?: loadSessions(false).firstOrNull()?.also { session ->
                loadEventsIntoCache(session.id)
            }
            ?: createSession()
    }

    override suspend fun getEventsFromSession(sessionId: String): Flow<Event> =
        reportException {
            if (sessionDataCache.eventCache.isEmpty()) {
                loadEventsIntoCache(sessionId)
            }

            return@reportException flow {
                sessionDataCache.eventCache.values.toList().forEach { emit(it) }
            }
        }

    /**
     * The reason is only used when we want to create an [ArtificialTerminationEvent].
     * If the session is closing for normal reasons (i.e. came to a normal end), then it should be `null`.
     */
    private suspend fun closeAllSessions(reason: Reason) {

        sessionDataCache.eventCache.clear()

        loadSessions(false).collect { closeSession(it, reason) }
    }

    override suspend fun closeCurrentSession(reason: Reason?) {
        closeSession(getCurrentCaptureSessionEvent(), reason)

        sessionDataCache.eventCache.clear()
    }

    /**
     * The reason is only used when we want to create an [ArtificialTerminationEvent].
     * If the session is closing for normal reasons (i.e. came to a normal end), then it should be `null`.
     */
    private suspend fun closeSession(sessionCaptureEvent: SessionCaptureEvent, reason: Reason?) {
        if (reason != null) {
            saveEvent(ArtificialTerminationEvent(timeHelper.now(), reason), sessionCaptureEvent)
        }

        sessionCaptureEvent.payload.endedAt = timeHelper.now()
        sessionCaptureEvent.payload.sessionIsClosed = true

        saveEvent(sessionCaptureEvent, sessionCaptureEvent)
    }

    private suspend fun loadSessions(isClosed: Boolean): Flow<SessionCaptureEvent> {
        return eventLocalDataSource.loadAllFromType(type = SESSION_CAPTURE)
            .map { it as SessionCaptureEvent }
            .filter { it.payload.sessionIsClosed == isClosed }
    }

    private suspend fun loadEventsIntoCache(sessionId: String) {
        eventLocalDataSource.loadAllFromSession(sessionId).collect {
            sessionDataCache.eventCache[it.id] = it
        }
    }

    private suspend fun <T> reportException(block: suspend () -> T): T =
        try {
            block()
        } catch (t: Throwable) {
            Timber.d(t)
            crashReportManager.logExceptionOrSafeException(t)
            throw t
        }

}
