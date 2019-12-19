package com.simprints.fingerprintscanner.v2.incoming

import com.simprints.fingerprintscanner.v2.domain.message.IncomingMessage
import com.simprints.fingerprintscanner.v2.domain.message.un20.Un20Response
import com.simprints.fingerprintscanner.v2.domain.message.vero.VeroEvent
import com.simprints.fingerprintscanner.v2.domain.message.vero.VeroResponse
import com.simprints.fingerprintscanner.v2.domain.packet.Channel
import com.simprints.fingerprintscanner.v2.incoming.message.accumulators.Un20ResponseAccumulator
import com.simprints.fingerprintscanner.v2.incoming.message.accumulators.VeroEventAccumulator
import com.simprints.fingerprintscanner.v2.incoming.message.accumulators.VeroResponseAccumulator
import com.simprints.fingerprintscanner.v2.incoming.message.toMessageStream
import com.simprints.fingerprintscanner.v2.incoming.packet.PacketRouter
import com.simprints.fingerprintscanner.v2.tools.lang.isSubclass
import com.simprints.fingerprintscanner.v2.tools.reactive.filterCast
import io.reactivex.Flowable
import io.reactivex.Single
import java.io.InputStream

class MessageInputStream(
    private val packetRouter: PacketRouter,
    private val veroResponseAccumulator: VeroResponseAccumulator,
    private val veroEventAccumulator: VeroEventAccumulator,
    private val un20ResponseAccumulator: Un20ResponseAccumulator
) : IncomingConnectable {

    lateinit var veroResponses: Flowable<VeroResponse>
    lateinit var veroEvents: Flowable<VeroEvent>
    lateinit var un20Responses: Flowable<Un20Response>

    override fun connect(inputStream: InputStream) {
        packetRouter.connect(inputStream)
        veroResponses = packetRouter.incomingPacketChannels[Channel.Remote.VeroServer]?.toMessageStream(veroResponseAccumulator)
            ?: throw TODO("exception handling")
        veroEvents = packetRouter.incomingPacketChannels[Channel.Remote.VeroEvent]?.toMessageStream(veroEventAccumulator)
            ?: throw TODO("exception handling")
        un20Responses = packetRouter.incomingPacketChannels[Channel.Remote.Un20Server]?.toMessageStream(un20ResponseAccumulator)
            ?: throw TODO("exception handling")
    }

    override fun disconnect() {
        packetRouter.disconnect()
    }

    inline fun <reified R : IncomingMessage> receiveResponse(): Single<R> =
        when {
            isSubclass<R, VeroResponse>() -> veroResponses
            isSubclass<R, Un20Response>() -> un20Responses
            else -> TODO("exception handling")
        }
            .filterCast<R>()
            .firstOrError()
}
