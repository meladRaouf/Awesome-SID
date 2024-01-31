package com.simprints.infra.eventsync.event.remote.models

import androidx.annotation.Keep
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include
import com.simprints.infra.config.store.models.TokenKeyType
import com.simprints.infra.events.event.domain.models.SuspiciousIntentEvent.SuspiciousIntentPayload

@Keep
@JsonInclude(Include.NON_NULL)
internal data class ApiSuspiciousIntentPayload(
    override val startTime: ApiTimestamp,
    override val version: Int,
    val unexpectedExtras: Map<String, Any?>,
) : ApiEventPayload(ApiEventPayloadType.SuspiciousIntent, version, startTime) {

    constructor(domainPayload: SuspiciousIntentPayload) : this(
        domainPayload.createdAt.fromDomainToApi(),
        domainPayload.eventVersion,
        domainPayload.unexpectedExtras,
    )

    override fun getTokenizedFieldJsonPath(tokenKeyType: TokenKeyType): String? =
        null // this payload doesn't have tokenizable fields
}

