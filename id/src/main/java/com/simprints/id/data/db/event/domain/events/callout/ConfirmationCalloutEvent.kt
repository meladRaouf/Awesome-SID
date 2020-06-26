package com.simprints.id.data.db.event.domain.events.callout

import androidx.annotation.Keep
import com.simprints.id.data.db.event.domain.events.Event
import com.simprints.id.data.db.event.domain.events.EventLabel
import com.simprints.id.data.db.event.domain.events.EventPayload
import com.simprints.id.data.db.event.domain.events.EventPayloadType
import java.util.*

@Keep
class ConfirmationCalloutEvent(
    creationTime: Long,
    projectId: String,
    selectedGuid: String,
    sessionId: String = UUID.randomUUID().toString() //StopShip: to change in PAS-993
) : Event(
    UUID.randomUUID().toString(),
    listOf(EventLabel.SessionId(sessionId)),
    ConfirmationCalloutPayload(creationTime, projectId, selectedGuid, sessionId)) {

    @Keep
    class ConfirmationCalloutPayload(
        creationTime: Long,
        val projectId: String,
        val selectedGuid: String,
        val sessionId: String
    ) : EventPayload(EventPayloadType.CALLOUT_CONFIRMATION, creationTime)
}
