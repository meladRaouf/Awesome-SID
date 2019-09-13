package com.simprints.id.orchestrator.cache.crypto

import com.simprints.id.data.secure.keystore.KeystoreManager
import com.simprints.id.domain.moduleapi.face.responses.FaceCaptureResponse
import com.simprints.id.orchestrator.steps.Step

class FaceCaptureResponseProcessor(
    keystoreManager: KeystoreManager
) : ResponseProcessor(keystoreManager) {

    override fun process(response: Step.Result?, operation: Operation): Step.Result {
        require(response is FaceCaptureResponse)

        val capturingResult = response.capturingResult.map {
            it.apply {
                result?.template?.let { template ->
                    val tmpTemplateString = String(template)
                    val processedTemplate = when (operation) {
                        Operation.ENCODE -> keystoreManager.encryptString(tmpTemplateString)
                        Operation.DECODE -> keystoreManager.decryptString(tmpTemplateString)
                    }.toByteArray()

                    val faceSample = result.copy(template = processedTemplate)
                    copy(result = faceSample)
                }
            }
        }

        return response.copy(capturingResult = capturingResult)
    }

}
