package com.simprints.fingerprintscanner.v2.domain.root.models


import com.simprints.fingerprintscanner.v2.tools.primitives.byteArrayOf as byteArrayOfAny

class CypressExtendedFirmwareVersion(val versionAsString: String) {

    fun getBytes(): ByteArray {
        val bytes = versionAsString.toByteArray()
        return byteArrayOfAny(bytes.size, bytes)
    }

    companion object {
        fun fromString(version: String) = CypressExtendedFirmwareVersion(
            versionAsString = version
        )
    }
}
