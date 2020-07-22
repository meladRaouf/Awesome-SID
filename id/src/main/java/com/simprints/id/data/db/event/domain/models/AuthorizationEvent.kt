package com.simprints.id.data.db.event.domain.models

import androidx.annotation.Keep
import com.simprints.id.data.db.event.domain.models.AuthorizationEvent.AuthorizationPayload.Result
import com.simprints.id.data.db.event.domain.models.AuthorizationEvent.AuthorizationPayload.UserInfo
import com.simprints.id.data.db.event.domain.models.EventLabel.SessionIdLabel
import com.simprints.id.data.db.event.domain.models.EventType.AUTHORIZATION
import java.util.*

@Keep
class AuthorizationEvent(
    createdAt: Long,
    result: Result,
    userInfo: UserInfo?,
    sessionId: String = UUID.randomUUID().toString() //StopShip: to change in PAS-993
) : Event(
    UUID.randomUUID().toString(),
    mutableListOf(SessionIdLabel(sessionId)),
    AuthorizationPayload(createdAt, EVENT_VERSION, result, userInfo),
    AUTHORIZATION) {

    @Keep
    class AuthorizationPayload(createdAt: Long,
                               eventVersion: Int,
                               val result: Result,
                               val userInfo: UserInfo?) : EventPayload(AUTHORIZATION, eventVersion, createdAt) {

        @Keep
        enum class Result {
            AUTHORIZED, NOT_AUTHORIZED
        }

        @Keep
        class UserInfo(val projectId: String, val userId: String)
    }

    companion object {
        const val EVENT_VERSION = DEFAULT_EVENT_VERSION
    }
}
