package com.simprints.id.data.db.event.domain.events.session

import androidx.annotation.Keep
import com.simprints.id.data.db.event.domain.events.Event
import com.simprints.id.data.db.event.domain.events.EventLabel
import com.simprints.id.data.db.event.domain.events.EventPayload
import com.simprints.id.data.db.event.domain.events.EventPayloadType
import java.util.*

@Keep
open class SessionEvent(creationTime: Long,
                        id: String = UUID.randomUUID().toString(),
                        projectId: String,
                        appVersionName: String,
                        libVersionName: String = "",
                        language: String,
                        device: Device,
                        databaseInfo: DatabaseInfo,
                        endTime: Long = 0,
                        location: Location? = null,
                        analyticsId: String? = null) : Event(
    UUID.randomUUID().toString(),
    listOf(EventLabel.SessionId(id)),
    SessionPayload(creationTime, id, projectId, appVersionName, libVersionName, language, device, databaseInfo, endTime, location, analyticsId)) {

    companion object {
        // When the sync starts, any open activeSession started GRACE_PERIOD ms
        // before it will be considered closed
        const val GRACE_PERIOD: Long = 1000 * 60 * 5 // 5 minutes
    }

    @Keep
    class SessionPayload(
        creationTime: Long,
        val id: String = UUID.randomUUID().toString(),
        val projectId: String,
        val appVersionName: String,
        val libVersionName: String = "",
        val language: String,
        val device: Device,
        val databaseInfo: DatabaseInfo,
        val endTime: Long = 0,
        val location: Location? = null,
        val analyticsId: String? = null
    ) : EventPayload(EventPayloadType.ONE_TO_ONE_MATCH, creationTime)
}
