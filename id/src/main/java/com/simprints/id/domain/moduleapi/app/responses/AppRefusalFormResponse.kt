package com.simprints.id.domain.moduleapi.app.responses

import com.simprints.id.domain.moduleapi.app.responses.entities.RefusalFormAnswer
import com.simprints.id.orchestrator.steps.Step
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
data class AppRefusalFormResponse(val answer: RefusalFormAnswer): AppResponse, Step.Result {

    @IgnoredOnParcel override val type: AppResponseType = AppResponseType.REFUSAL
}
