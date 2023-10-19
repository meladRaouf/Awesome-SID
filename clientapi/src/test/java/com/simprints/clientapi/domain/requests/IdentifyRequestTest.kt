package com.simprints.clientapi.domain.requests

import com.google.common.truth.Truth.assertThat
import com.simprints.clientapi.requestFactories.RequestFactory.Companion.MOCK_PROJECT_ID
import com.simprints.core.domain.tokenization.asTokenizableEncrypted
import com.simprints.core.domain.tokenization.asTokenizableRaw
import com.simprints.moduleapi.app.requests.IAppIdentifyRequest
import org.junit.Test


class IdentifyRequestTest {

    @Test
    fun `when request is converted to app request, tokenization flag is set correctly`() {
        listOf(true, false).forEach { isTokenized ->
            val userId = with("userId") {
                if (isTokenized) asTokenizableEncrypted() else asTokenizableRaw()
            }
            val moduleId = with("moduleId") {
                if (isTokenized) asTokenizableEncrypted() else asTokenizableRaw()
            }
            val request = IdentifyRequest(
                projectId = MOCK_PROJECT_ID,
                userId = userId,
                unknownExtras = emptyMap(),
                moduleId = moduleId,
                metadata = ""
            )
            val result = request.convertToAppRequest() as IAppIdentifyRequest
            assertThat(result.isUserIdTokenized).isEqualTo(isTokenized)
            assertThat(result.isModuleIdTokenized).isEqualTo(isTokenized)
        }
    }
}