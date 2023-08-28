package com.simprints.fingerprint.biosdk

import com.simprints.fingerprint.infra.basebiosdk.matching.domain.FingerprintIdentity
import com.simprints.fingerprint.infra.basebiosdk.matching.domain.MatchResult
import com.simprints.fingerprint.scanner.domain.AcquireImageResponse
import com.simprints.fingerprint.scanner.domain.CaptureFingerprintResponse
import com.simprints.infra.config.domain.models.Vero2Configuration

@Suppress("unused") // This class will be used once we have the NEC SDK integrated
class NECBioSdkWrapper: BioSdkWrapper {
    override suspend fun initialize() {
        TODO("Not yet implemented")
    }

    override suspend fun match(
        probe: FingerprintIdentity,
        candidates: List<FingerprintIdentity>,
        isCrossFingerMatchingEnabled: Boolean
    ): List<MatchResult> {
        TODO("Not yet implemented")
    }

    override suspend fun acquireFingerprintTemplate(
        captureFingerprintStrategy: Vero2Configuration.CaptureStrategy?,
        timeOutMs: Int,
        qualityThreshold: Int
    ): CaptureFingerprintResponse {
        TODO("Not yet implemented")
    }

    override suspend fun acquireFingerprintImage(): AcquireImageResponse {
        TODO("Not yet implemented")
    }

}
