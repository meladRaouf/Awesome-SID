package com.simprints.id.data.db.event.remote.events.face

import androidx.annotation.Keep
import com.simprints.id.data.db.event.domain.models.face.FaceCaptureRetryEvent
import com.simprints.id.data.db.event.domain.models.face.FaceCaptureRetryEvent.FaceCaptureRetryPayload
import com.simprints.id.data.db.event.remote.events.ApiEvent
import com.simprints.id.data.db.event.remote.events.ApiEventPayload
import com.simprints.id.data.db.event.remote.events.ApiEventPayloadType.FACE_CAPTURE_RETRY
import com.simprints.id.data.db.event.remote.events.fromDomainToApi

@Keep
class ApiFaceCaptureRetryEvent(
    val domainEvent: FaceCaptureRetryEvent
) : ApiEvent(
    domainEvent.id,
    domainEvent.labels.fromDomainToApi(),
    domainEvent.payload.fromDomainToApi()) {

    @Keep
    class ApiFaceCaptureRetryPayload(createdAt: Long,
                                     val endedAt: Long,
                                     version: Int) : ApiEventPayload(FACE_CAPTURE_RETRY, version, createdAt) {

        constructor(domainPayload: FaceCaptureRetryPayload) : this(
            domainPayload.createdAt,
            domainPayload.endedAt,
            domainPayload.eventVersion)
    }
}
