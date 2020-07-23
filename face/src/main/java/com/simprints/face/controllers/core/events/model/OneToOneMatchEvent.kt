package com.simprints.face.controllers.core.events.model

import androidx.annotation.Keep
import com.simprints.face.exceptions.FaceUnexpectedException
import com.simprints.id.data.db.subject.local.SubjectLocalDataSource
import java.io.Serializable
import com.simprints.id.data.db.event.domain.models.OneToOneMatchEvent as CoreOneToOneMatchEvent

@Keep
class OneToOneMatchEvent(
    startTime: Long,
    endTime: Long,
    val query: Serializable,
    val matcher: Matcher,
    val result: MatchEntry?
) : Event(EventType.ONE_TO_ONE_MATCH, startTime, endTime) {

    fun fromDomainToCore() = CoreOneToOneMatchEvent(
        startTime,
        endTime,
        (query as SubjectLocalDataSource.Query).extractVerifyId(),
        matcher.fromDomainToCore(),
        result?.fromDomainToCore()
    )

    private fun SubjectLocalDataSource.Query.extractVerifyId() =
        subjectId
            ?: throw FaceUnexpectedException("null personId in candidate query when saving OneToOneMatchEvent")

}
