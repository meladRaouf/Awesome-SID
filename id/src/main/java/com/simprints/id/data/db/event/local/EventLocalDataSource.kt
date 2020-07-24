package com.simprints.id.data.db.event.local

import com.simprints.id.data.db.event.domain.models.Event
import com.simprints.id.data.db.event.domain.models.EventType
import com.simprints.id.data.db.event.domain.models.session.SessionCaptureEvent
import kotlinx.coroutines.flow.Flow

interface EventLocalDataSource {

    data class EventQuery(val id: String? = null,
                          val type: EventType? = null,
                          val projectId: String? = null,
                          val subjectId: String? = null,
                          val attendantId: String? = null,
                          val sessionId: String? = null,
                          val deviceId: String? = null,
                          val startTime: LongRange? = null,
                          val endTime: LongRange? = null)

    suspend fun create(appVersionName: String,
                       libSimprintsVersionName: String,
                       language: String,
                       deviceId: String): String

    suspend fun count(query: EventQuery = EventQuery()): Int
    suspend fun load(query: EventQuery = EventQuery()): Flow<Event>
    suspend fun delete(query: EventQuery = EventQuery())

    suspend fun insertOrUpdate(event: Event)
    suspend fun insertOrUpdateInCurrentSession(event: Event)

    suspend fun getCurrentSessionCaptureEvent(): SessionCaptureEvent
}
