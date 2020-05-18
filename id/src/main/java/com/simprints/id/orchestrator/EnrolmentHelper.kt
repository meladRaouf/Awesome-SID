package com.simprints.id.orchestrator

import com.simprints.id.data.db.person.domain.Person
import com.simprints.id.domain.moduleapi.app.requests.AppRequest
import com.simprints.id.domain.moduleapi.app.requests.AppRequest.AppRequestFlow.AppEnrolRequest
import com.simprints.id.domain.moduleapi.face.responses.FaceCaptureResponse
import com.simprints.id.domain.moduleapi.fingerprint.responses.FingerprintCaptureResponse
import com.simprints.id.tools.TimeHelper

interface EnrolmentHelper {

    fun buildPerson(request: AppRequest.AppRequestFlow,
                    fingerprintResponse: FingerprintCaptureResponse?,
                    faceResponse: FaceCaptureResponse?,
                    timeHelper: TimeHelper): Person

    suspend fun enrol(person: Person)
}
