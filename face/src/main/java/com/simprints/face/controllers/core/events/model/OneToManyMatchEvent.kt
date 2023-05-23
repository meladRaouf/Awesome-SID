package com.simprints.face.controllers.core.events.model

import androidx.annotation.Keep
import com.simprints.infra.enrolment.records.domain.models.SubjectQuery
import java.io.Serializable
import com.simprints.infra.events.event.domain.models.OneToManyMatchEvent as CoreOneToManyMatchEvent
import com.simprints.infra.events.event.domain.models.OneToManyMatchEvent.OneToManyMatchPayload.MatchPool as CoreMatchPool
import com.simprints.infra.events.event.domain.models.OneToManyMatchEvent.OneToManyMatchPayload.MatchPoolType as CoreMatchPoolType

@Keep
class OneToManyMatchEvent(
    startTime: Long,
    endTime: Long,
    val query: Serializable,
    val count: Int,
    val matcher: String,
    val result: List<MatchEntry>?
) : Event(EventType.ONE_TO_MANY_MATCH, startTime, endTime) {

    fun fromDomainToCore() = CoreOneToManyMatchEvent(
        startTime,
        endTime,
        (query as SubjectQuery).asCoreMatchPool(count),
        matcher,
        result?.map { it.fromDomainToCore() }
    )

    private fun SubjectQuery.asCoreMatchPool(count: Int) =
        CoreMatchPool(this.parseQueryAsCoreMatchPoolType(), count)

    private fun SubjectQuery.parseQueryAsCoreMatchPoolType(): CoreMatchPoolType =
        when {
            this.attendantId != null -> CoreMatchPoolType.USER
            this.moduleId != null -> CoreMatchPoolType.MODULE
            else -> CoreMatchPoolType.PROJECT
        }
}
