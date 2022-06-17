package com.simprints.eventsystem.event.domain.models.callout

import androidx.annotation.Keep
import com.simprints.eventsystem.event.domain.models.Event
import com.simprints.eventsystem.event.domain.models.EventLabels
import com.simprints.eventsystem.event.domain.models.EventPayload
import com.simprints.eventsystem.event.domain.models.EventType
import com.simprints.eventsystem.event.domain.models.EventType.CALLOUT_LAST_BIOMETRICS
import java.util.UUID

@Keep
data class EnrolmentLastBiometricsCalloutEvent(
    override val id: String = UUID.randomUUID().toString(),
    override var labels: EventLabels,
    override val payload: EnrolmentLastBiometricsCalloutPayload,
    override val type: EventType
) : Event() {

    constructor(
        createdAt: Long,
        projectId: String,
        userId: String,
        moduleId: String,
        metadata: String?,
        sessionId: String,
        labels: EventLabels = EventLabels()
    ) : this(
        UUID.randomUUID().toString(),
        labels,
        EnrolmentLastBiometricsCalloutPayload(createdAt, EVENT_VERSION, projectId, userId, moduleId, metadata, sessionId),
        CALLOUT_LAST_BIOMETRICS)

    @Keep
    data class EnrolmentLastBiometricsCalloutPayload(
        override val createdAt: Long,
        override val eventVersion: Int,
        val projectId: String,
        val userId: String,
        val moduleId: String,
        val metadata: String?,
        val sessionId: String,
        override val type: EventType = CALLOUT_LAST_BIOMETRICS,
        override val endedAt: Long = 0) : EventPayload()

    companion object {
        const val EVENT_VERSION = 1
    }
}
