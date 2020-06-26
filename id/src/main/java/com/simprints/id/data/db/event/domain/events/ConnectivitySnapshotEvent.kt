package com.simprints.id.data.db.event.domain.events

import androidx.annotation.Keep
import com.simprints.id.tools.TimeHelper
import com.simprints.id.tools.utils.SimNetworkUtils
import java.util.*

@Keep
class ConnectivitySnapshotEvent(
    creationTime: Long,
    networkType: String,
    connections: List<SimNetworkUtils.Connection>,
    sessionId: String = UUID.randomUUID().toString() //StopShip: to change in PAS-993
) : Event(
    UUID.randomUUID().toString(),
    listOf(EventLabel.SessionId(sessionId)),
    ConnectivitySnapshotPayload(creationTime, networkType, connections)) {

    @Keep
    class ConnectivitySnapshotPayload(
        startTime: Long,
        val networkType: String,
        val connections: List<SimNetworkUtils.Connection>
    ) : EventPayload(EventPayloadType.CONNECTIVITY_SNAPSHOT, startTime) {

        companion object {
            fun buildEvent(simNetworkUtils: SimNetworkUtils,
                           timeHelper: TimeHelper): ConnectivitySnapshotEvent {

                return simNetworkUtils.let {
                    ConnectivitySnapshotEvent(
                        timeHelper.now(),
                        it.mobileNetworkType ?: "",
                        it.connectionsStates)
                }
            }
        }
    }
}
