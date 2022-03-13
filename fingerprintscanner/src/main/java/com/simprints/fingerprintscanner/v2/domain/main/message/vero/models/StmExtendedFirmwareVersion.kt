package com.simprints.fingerprintscanner.v2.domain.main.message.vero.models

import com.simprints.fingerprintscanner.v2.tools.primitives.byteArrayOf as byteArrayOfAny


class StmExtendedFirmwareVersion(val versionAsString: String) {

    fun getBytes(): ByteArray {
        val bytes = versionAsString.toByteArray()
        return byteArrayOfAny(bytes.size, bytes)
    }

    companion object {
        fun fromString(version: String) = StmExtendedFirmwareVersion(
            versionAsString = version
        )

        fun fromBytes(bytes: ByteArray) = StmExtendedFirmwareVersion(
            versionAsString = String(bytes)
        )
    }
}
