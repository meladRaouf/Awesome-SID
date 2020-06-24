package com.simprints.id.data.db.event.domain.events

import androidx.annotation.Keep
import com.simprints.id.data.db.event.domain.events.AuthorizationEvent.AuthorizationPayload.Result
import com.simprints.id.data.db.event.domain.events.AuthorizationEvent.AuthorizationPayload.UserInfo
import com.simprints.id.data.db.event.domain.events.EventPayloadType.AUTHORIZATION
import java.util.*

@Keep
class AuthorizationEvent(
    startTime: Long,
    result: Result,
    userInfo: UserInfo?,
    sessionId: String = UUID.randomUUID().toString(), //StopShip: to change in PAS-993
    sessionStartTime: Long = 0 //StopShip: to change in PAS-993
) : Event(
    UUID.randomUUID().toString(),
    listOf(EventLabel.SessionId(sessionId)),
    AuthorizationPayload(startTime, startTime - sessionStartTime, result, userInfo)) {

    @Keep
    class AuthorizationPayload(startTime: Long,
                               relativeStartTime: Long,
                               val result: Result,
                               val userInfo: UserInfo?) : EventPayload(AUTHORIZATION, startTime, relativeStartTime) {

        @Keep
        enum class Result {
            AUTHORIZED, NOT_AUTHORIZED
        }

        @Keep
        class UserInfo(val projectId: String, val userId: String)
    }
}
