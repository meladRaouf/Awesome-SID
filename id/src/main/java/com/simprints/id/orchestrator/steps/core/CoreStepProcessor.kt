package com.simprints.id.orchestrator.steps.core

import android.content.Intent
import com.simprints.id.domain.moduleapi.core.requests.ConsentType
import com.simprints.id.orchestrator.steps.Step

interface CoreStepProcessor {

    fun buildStepConsent(consentType: ConsentType): Step

    fun buildFetchGuidStep(projectId: String, verifyGuid: String): Step

    fun buildIdentityConfirmationStep(projectId: String, sessionId: String, selectedGuid: String): Step

    fun processResult(data: Intent?): Step.Result?
}
