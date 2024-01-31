package com.simprints.feature.orchestrator.usecases

import com.simprints.core.ExternalScope
import com.simprints.core.domain.response.AppMatchConfidence
import com.simprints.core.domain.response.AppResponseTier
import com.simprints.core.tools.time.TimeHelper
import com.simprints.infra.events.EventRepository
import com.simprints.infra.events.event.domain.models.callback.CallbackComparisonScore
import com.simprints.infra.events.event.domain.models.callback.ConfirmationCallbackEvent
import com.simprints.infra.events.event.domain.models.callback.EnrolmentCallbackEvent
import com.simprints.infra.events.event.domain.models.callback.ErrorCallbackEvent
import com.simprints.infra.events.event.domain.models.callback.IdentificationCallbackEvent
import com.simprints.infra.events.event.domain.models.callback.RefusalCallbackEvent
import com.simprints.infra.events.event.domain.models.callback.VerificationCallbackEvent
import com.simprints.infra.orchestration.data.responses.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

internal class AddCallbackEventUseCase @Inject constructor(
    private val eventRepository: EventRepository,
    private val timeHelper: TimeHelper,
    @ExternalScope private val externalScope: CoroutineScope,
) {

    operator fun invoke(result: AppResponse) {
        val callbackEvent = when (result) {
            is AppEnrolResponse -> buildEnrolmentCallbackEvent(result)
            is AppIdentifyResponse -> buildIdentificationCallbackEvent(result)
            is AppVerifyResponse -> buildVerificationCallbackEvent(result)
            is AppConfirmationResponse -> buildConfirmIdentityCallbackEvent(result)
            is AppRefusalResponse -> buildRefusalCallbackEvent(result)
            is AppErrorResponse -> buildErrorCallbackEvent(result)
        }

        externalScope.launch { eventRepository.addOrUpdateEvent(callbackEvent) }
    }

    private fun buildEnrolmentCallbackEvent(appResponse: AppEnrolResponse) = EnrolmentCallbackEvent(
        timeHelper.nowTimestamp(),
        appResponse.guid
    )

    private fun buildIdentificationCallbackEvent(appResponse: AppIdentifyResponse) =
        IdentificationCallbackEvent(
            timeHelper.nowTimestamp(),
            appResponse.sessionId,
            appResponse.identifications.map { buildComparisonScore(it) },
        )

    private fun buildVerificationCallbackEvent(appResponse: AppVerifyResponse) =
        VerificationCallbackEvent(
            timeHelper.nowTimestamp(),
            buildComparisonScore(appResponse.matchResult),
        )

    private fun buildComparisonScore(matchResult: AppMatchResult) = CallbackComparisonScore(
        matchResult.guid,
        matchResult.confidenceScore,
        AppResponseTier.valueOf(matchResult.tier.name),
        AppMatchConfidence.valueOf(matchResult.matchConfidence.name),
    )

    private fun buildRefusalCallbackEvent(appResponse: AppRefusalResponse) = RefusalCallbackEvent(
        timeHelper.nowTimestamp(),
        appResponse.reason,
        appResponse.extra,
    )

    private fun buildErrorCallbackEvent(appResponse: AppErrorResponse) = ErrorCallbackEvent(
        timeHelper.nowTimestamp(),
        ErrorCallbackEvent.ErrorCallbackPayload.Reason.fromAppResponseErrorReasonToEventReason(
            appResponse.reason
        ),
    )

    private fun buildConfirmIdentityCallbackEvent(appResponse: AppConfirmationResponse) =
        ConfirmationCallbackEvent(
            timeHelper.nowTimestamp(),
            appResponse.identificationOutcome,
        )
}
