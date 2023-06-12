package com.simprints.id.orchestrator.modality

import android.content.Intent
import com.simprints.id.domain.moduleapi.app.requests.AppRequest
import com.simprints.id.domain.moduleapi.app.requests.AppRequest.AppRequestFollowUp.AppConfirmIdentityRequest
import com.simprints.id.orchestrator.steps.Step
import com.simprints.id.orchestrator.steps.core.CoreRequestCode.Companion.isCoreResult
import com.simprints.id.orchestrator.steps.core.CoreStepProcessor
import javax.inject.Inject

class ModalityFlowConfirmIdentity @Inject constructor(private val coreStepProcessor: CoreStepProcessor) :
    ModalityFlow {

    override val steps: MutableList<Step> = mutableListOf()

    override suspend fun startFlow(appRequest: AppRequest) {
        require(appRequest is AppConfirmIdentityRequest)
        steps.addAll(buildStepsList(appRequest))
    }

    private fun buildStepsList(appRequest: AppConfirmIdentityRequest) =
        listOf(
            coreStepProcessor.buildConfirmIdentityStep(
                appRequest.projectId,
                appRequest.selectedGuid
            )
        )

    override fun restoreState(stepsToRestore: List<Step>) {
        steps.clear()
        steps.addAll(stepsToRestore)
    }

    override fun getNextStepToLaunch(): Step? =
        steps.firstOrNull { it.getStatus() == Step.Status.NOT_STARTED }

    override suspend fun handleIntentResult(
        appRequest: AppRequest,
        requestCode: Int,
        resultCode: Int, data: Intent?
    ): Step? {
        val result = when {
            isCoreResult(requestCode) -> coreStepProcessor.processResult(data)
            else -> throw IllegalStateException("Invalid result from intent")
        }

        val stepForRequest = steps.firstOrNull { it.requestCode == requestCode }
        return stepForRequest?.apply { setResult(result) }
    }
}
