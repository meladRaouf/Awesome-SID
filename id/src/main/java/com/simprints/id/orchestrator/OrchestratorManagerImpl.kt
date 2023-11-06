package com.simprints.id.orchestrator

import android.content.Intent
import androidx.lifecycle.MutableLiveData
import com.simprints.core.domain.common.FlowProvider
import com.simprints.core.domain.common.FlowProvider.FlowType.ENROL
import com.simprints.core.domain.common.FlowProvider.FlowType.IDENTIFY
import com.simprints.core.domain.common.FlowProvider.FlowType.VERIFY
import com.simprints.core.tools.time.TimeHelper
import com.simprints.feature.setup.LocationStore
import com.simprints.id.domain.moduleapi.app.requests.AppRequest
import com.simprints.id.domain.moduleapi.app.requests.AppRequest.AppRequestFlow.AppEnrolRequest
import com.simprints.id.domain.moduleapi.app.requests.AppRequest.AppRequestFlow.AppIdentifyRequest
import com.simprints.id.domain.moduleapi.app.requests.AppRequest.AppRequestFlow.AppVerifyRequest
import com.simprints.id.domain.moduleapi.app.requests.AppRequest.AppRequestFollowUp.AppConfirmIdentityRequest
import com.simprints.id.domain.moduleapi.app.requests.AppRequest.AppRequestFollowUp.AppEnrolLastBiometricsRequest
import com.simprints.id.domain.moduleapi.app.responses.AppResponse
import com.simprints.id.domain.moduleapi.app.responses.AppResponseType
import com.simprints.id.domain.moduleapi.face.responses.FaceCaptureResponse
import com.simprints.id.domain.moduleapi.fingerprint.responses.FingerprintCaptureResponse
import com.simprints.id.orchestrator.cache.HotCache
import com.simprints.id.orchestrator.modality.ModalityFlow
import com.simprints.id.orchestrator.responsebuilders.AppResponseFactory
import com.simprints.id.orchestrator.steps.Step
import com.simprints.id.orchestrator.steps.Step.Status.ONGOING
import com.simprints.id.orchestrator.steps.face.FaceRequestCode
import com.simprints.id.orchestrator.steps.fingerprint.FingerprintRequestCode
import com.simprints.infra.config.store.models.GeneralConfiguration
import com.simprints.infra.recent.user.activity.RecentUserActivityManager
import javax.inject.Inject

class OrchestratorManagerImpl @Inject constructor(
    private val flowModalityFactory: ModalityFlowFactory,
    private val appResponseFactory: AppResponseFactory,
    private val hotCache: HotCache,
    private val recentUserActivityManager: RecentUserActivityManager,
    private val timeHelper: TimeHelper,
    private val personCreationEventHelper: PersonCreationEventHelper,
    private val locationStore: LocationStore
) : OrchestratorManager, FlowProvider {

    override val ongoingStep = MutableLiveData<Step?>()
    override val appResponse = MutableLiveData<AppResponse?>()

    internal lateinit var modalities: List<GeneralConfiguration.Modality>
    internal var sessionId: String = ""

    private lateinit var modalitiesFlow: ModalityFlow

    override suspend fun initialise(
        modalities: List<GeneralConfiguration.Modality>,
        appRequest: AppRequest,
        sessionId: String
    ) {
        this.sessionId = sessionId
        hotCache.appRequest = appRequest
        this.modalities = modalities
        modalitiesFlow = flowModalityFactory.createModalityFlow(appRequest)
        resetInternalState()
    }

    override suspend fun startModalityFlow() {
        proceedToNextStepOrAppResponse()
    }

    override suspend fun handleIntentResult(
        appRequest: AppRequest,
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        modalitiesFlow.handleIntentResult(appRequest, requestCode, resultCode, data)

        if (appRequest !is AppRequest.AppRequestFollowUp) {
            val fingerprintCaptureCompleted =
                !modalities.contains(GeneralConfiguration.Modality.FINGERPRINT) ||
                    modalitiesFlow.steps.filter { it.requestCode == FingerprintRequestCode.CAPTURE.value }
                        .all { it.getResult() is FingerprintCaptureResponse }

            val faceCaptureCompleted =
                !modalities.contains(GeneralConfiguration.Modality.FACE) ||
                    modalitiesFlow.steps.filter { it.requestCode == FaceRequestCode.CAPTURE.value }
                        .all { it.getResult() is FaceCaptureResponse }


            if (fingerprintCaptureCompleted && faceCaptureCompleted) {
                personCreationEventHelper.addPersonCreationEventIfNeeded(modalitiesFlow.steps.mapNotNull { it.getResult() })
            }
        }

        proceedToNextStepOrAppResponse()
    }

    override suspend fun restoreState() {
        resetInternalState()
        hotCache.load().let(modalitiesFlow::restoreState)
        proceedToNextStepOrAppResponse()
    }

    override suspend fun saveState() {
        hotCache.save(modalitiesFlow.steps)
    }

    override fun getCurrentFlow() =
        when (hotCache.appRequest) {
            is AppEnrolRequest -> ENROL
            is AppIdentifyRequest -> IDENTIFY
            is AppVerifyRequest -> VERIFY
            is AppEnrolLastBiometricsRequest -> throw IllegalStateException("Not running one of the main flows")
            is AppConfirmIdentityRequest -> throw IllegalStateException("Not running one of the main flows")
        }

    private suspend fun proceedToNextStepOrAppResponse() {
        with(modalitiesFlow) {
            if (!anyStepOngoing()) {
                val potentialNextStep = getNextStepToLaunch()
                if (potentialNextStep != null) {
                    startStep(potentialNextStep)
                } else {
                    buildAppResponseAndUpdateDailyActivity()
                    // Acquiring location info could take long time, so we should stop location tracker
                    // before returning to the caller app to avoid creating empty sessions.
                    locationStore.cancelLocationCollection()
                }
            }
        }
    }

    private fun startStep(step: Step) {
        step.setStatus(ONGOING)
        ongoingStep.value = step
        appResponse.value = null
    }

    private fun ModalityFlow.anyStepOngoing() = steps.any { it.getStatus() == ONGOING }

    private suspend fun buildAppResponseAndUpdateDailyActivity() {
        val steps = modalitiesFlow.steps
        val appResponseToReturn = appResponseFactory.buildAppResponse(
            modalities, hotCache.appRequest, steps, sessionId
        )

        updateDailyActivity(appResponseToReturn)
        ongoingStep.value = null
        appResponse.value = appResponseToReturn
    }

    private suspend fun updateDailyActivity(appResponse: AppResponse) {
        when (appResponse.type) {
            AppResponseType.ENROL -> recentUserActivityManager.updateRecentUserActivity {
                it.apply {
                    it.enrolmentsToday++
                    it.lastActivityTime = timeHelper.now()
                }
            }

            AppResponseType.IDENTIFY -> recentUserActivityManager.updateRecentUserActivity {
                it.apply {
                    it.identificationsToday++
                    it.lastActivityTime = timeHelper.now()
                }
            }

            AppResponseType.VERIFY -> recentUserActivityManager.updateRecentUserActivity {
                it.apply {
                    it.verificationsToday++
                    it.lastActivityTime = timeHelper.now()
                }
            }

            AppResponseType.REFUSAL,
            AppResponseType.CONFIRMATION,
            AppResponseType.ERROR -> {
                //Other cases are ignore and we don't show info in dashboard for it
            }
        }
    }

    private fun resetInternalState() {
        appResponse.value = null
        ongoingStep.value = null
    }
}
