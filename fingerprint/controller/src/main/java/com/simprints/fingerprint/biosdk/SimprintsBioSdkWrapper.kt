package com.simprints.fingerprint.biosdk

import com.simprints.fingerprint.data.domain.fingerprint.toDomain
import com.simprints.fingerprint.infra.basebiosdk.FingerprintBioSdk
import com.simprints.fingerprint.infra.basebiosdk.acquisition.domain.ImageResponse
import com.simprints.fingerprint.infra.basebiosdk.acquisition.domain.TemplateResponse
import com.simprints.fingerprint.infra.basebiosdk.matching.domain.FingerprintIdentity
import com.simprints.fingerprint.infra.biosdkimpl.acquisition.template.FingerprintTemplateAcquisitionSettings
import com.simprints.fingerprint.infra.biosdkimpl.acquisition.template.FingerprintTemplateMetadata
import com.simprints.fingerprint.infra.biosdkimpl.matching.SimAfisMatcherSettings
import com.simprints.fingerprint.infra.scanner.domain.fingerprint.AcquireFingerprintImageResponse
import com.simprints.fingerprint.infra.scanner.domain.fingerprint.AcquireFingerprintTemplateResponse
import com.simprints.infra.config.domain.models.Vero2Configuration
import javax.inject.Inject


class SimprintsBioSdkWrapper @Inject constructor(
    private val bioSdk: FingerprintBioSdk<Unit, Unit, Unit, FingerprintTemplateAcquisitionSettings, FingerprintTemplateMetadata, SimAfisMatcherSettings>
) : BioSdkWrapper {


    override suspend fun initialize() {
        bioSdk.initialize()
    }

    override suspend fun match(
        probe: FingerprintIdentity,
        candidates: List<FingerprintIdentity>, isCrossFingerMatchingEnabled: Boolean
    ) = bioSdk.match(probe, candidates, SimAfisMatcherSettings(isCrossFingerMatchingEnabled))

    override suspend fun acquireFingerprintTemplate(
        captureFingerprintStrategy: Vero2Configuration.CaptureStrategy?,
        timeOutMs: Int,
        qualityThreshold: Int
    ): AcquireFingerprintTemplateResponse {
        val settings = FingerprintTemplateAcquisitionSettings(
            captureFingerprintStrategy?.toDomain(),
            timeOutMs,
            qualityThreshold
        )
        return bioSdk.acquireFingerprintTemplate(settings).toDomain()
    }

    override suspend fun acquireFingerprintImage(): AcquireFingerprintImageResponse {
        return bioSdk.acquireFingerprintImage().toDomain()
    }
}

private fun ImageResponse<Unit>.toDomain() = AcquireFingerprintImageResponse(this.imageBytes)

private fun TemplateResponse<FingerprintTemplateMetadata>.toDomain(): AcquireFingerprintTemplateResponse {
    require(templateMetadata != null) {
        "Template metadata should not be null"
    }
    return AcquireFingerprintTemplateResponse(
        template,
        templateMetadata!!.templateFormat,
        templateMetadata!!.imageQualityScore
    )
}
