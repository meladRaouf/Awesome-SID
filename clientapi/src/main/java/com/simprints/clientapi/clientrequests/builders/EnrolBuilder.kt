package com.simprints.clientapi.clientrequests.builders

import com.simprints.clientapi.clientrequests.extractors.EnrolExtractor
import com.simprints.clientapi.clientrequests.validators.EnrolValidator
import com.simprints.clientapi.domain.requests.BaseRequest
import com.simprints.clientapi.domain.requests.EnrolRequest
import com.simprints.core.domain.tokenization.asTokenizableRaw
import com.simprints.infra.config.store.models.Project
import com.simprints.infra.config.store.models.TokenKeyType
import com.simprints.infra.config.store.tokenization.TokenizationProcessor

class EnrolBuilder(
    private val extractor: EnrolExtractor,
    private val project: Project?,
    private val tokenizationProcessor: TokenizationProcessor,
    validator: EnrolValidator
) : ClientRequestBuilder(validator) {
    override fun encryptIfNecessary(baseRequest: BaseRequest): BaseRequest {
        val request = (baseRequest as? EnrolRequest) ?: return baseRequest
        val encryptedUserId = encryptField(
            value = request.userId,
            project = project,
            tokenKeyType = TokenKeyType.AttendantId,
            tokenizationProcessor = tokenizationProcessor
        )
        val encryptedModuleId = encryptField(
            value = request.moduleId,
            project = project,
            tokenKeyType = TokenKeyType.ModuleId,
            tokenizationProcessor = tokenizationProcessor
        )
        return request.copy(userId = encryptedUserId, moduleId = encryptedModuleId)
    }

    override fun buildAppRequest(): BaseRequest = EnrolRequest(
        projectId = extractor.getProjectId(),
        userId = extractor.getUserId().asTokenizableRaw(),
        metadata = extractor.getMetadata(),
        moduleId = extractor.getModuleId().asTokenizableRaw(),
        unknownExtras = extractor.getUnknownExtras()
    )
}
