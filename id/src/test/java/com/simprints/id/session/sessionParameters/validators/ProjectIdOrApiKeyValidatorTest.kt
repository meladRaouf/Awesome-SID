package com.simprints.id.session.sessionParameters.validators

import com.simprints.id.session.callout.CalloutAction
import com.simprints.id.session.sessionParameters.SessionParameters
import com.simprints.testframework.common.syntax.assertThrows
import org.junit.Test

class ProjectIdOrApiKeyValidatorTest {

    private val sessionParametersWithApiKey = SessionParameters(CalloutAction.IDENTIFY, "some_key", "", "", "", "", "", "", "")
    private val sessionParametersWithProjectId = SessionParameters(CalloutAction.IDENTIFY, "", "some_project_id", "", "", "", "", "", "")
    private val invalidSessionParameters = SessionParameters(CalloutAction.IDENTIFY, "", "", "", "", "", "", "", "")

    private val missingProjectIdOrApiKeyError = Error()
    private val validator = ProjectIdOrApiKeyValidator(missingProjectIdOrApiKeyError)

    @Test
    fun sessionParametersWithApiKey_shouldNotThrowException() {
        validator.validate(sessionParametersWithApiKey)
    }

    @Test
    fun sessionParametersWithProjectId_shouldNotThrowException() {
        validator.validate(sessionParametersWithProjectId)
    }

    @Test
    fun sessionParametersWithNoProjectIdOrApiKey_shouldThrowException() {
        assertThrows(missingProjectIdOrApiKeyError) {
            validator.validate(invalidSessionParameters)
        }
    }
}
