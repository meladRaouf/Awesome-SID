package com.simprints.fingerprint.infra.scanner.v2.domain.root.responses

import com.simprints.fingerprint.infra.scanner.v2.domain.root.RootMessageType
import com.simprints.fingerprint.infra.scanner.v2.domain.root.RootResponse

class EnterMainModeResponse : RootResponse(RootMessageType.ENTER_MAIN_MODE) {
    companion object {
        fun fromBytes(
            @Suppress("unused_parameter") data: ByteArray,
        ) = EnterMainModeResponse()
    }
}
