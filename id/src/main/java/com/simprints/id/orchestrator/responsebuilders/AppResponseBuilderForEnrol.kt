package com.simprints.id.orchestrator.responsebuilders

import com.simprints.id.data.db.subject.domain.FaceSample
import com.simprints.id.data.db.subject.domain.FingerprintSample
import com.simprints.id.data.db.subject.domain.Subject
import com.simprints.id.domain.modality.Modality
import com.simprints.id.domain.moduleapi.app.requests.AppEnrolRequest
import com.simprints.id.domain.moduleapi.app.requests.AppRequest
import com.simprints.id.domain.moduleapi.app.responses.AppEnrolResponse
import com.simprints.id.domain.moduleapi.app.responses.AppResponse
import com.simprints.id.domain.moduleapi.face.responses.FaceCaptureResponse
import com.simprints.id.domain.moduleapi.fingerprint.responses.FingerprintCaptureResponse
import com.simprints.id.orchestrator.EnrolmentHelper
import com.simprints.id.orchestrator.steps.Step
import com.simprints.id.tools.TimeHelper
import java.util.*

class AppResponseBuilderForEnrol(
    private val enrolmentHelper: EnrolmentHelper,
    private val timeHelper: TimeHelper
) : BaseAppResponseBuilder() {

    override suspend fun buildAppResponse(modalities: List<Modality>,
                                          appRequest: AppRequest,
                                          steps: List<Step>,
                                          sessionId: String): AppResponse {
        super.getErrorOrRefusalResponseIfAny(steps)?.let {
            return it
        }

        val request = appRequest as AppEnrolRequest
        val results = steps.map { it.getResult() }
        val faceResponse = getFaceCaptureResponse(results)
        val fingerprintResponse = getFingerprintCaptureResponse(results)

        val subject = SubjectBuilder.buildSubject(request, fingerprintResponse, faceResponse, timeHelper)
        with(enrolmentHelper) {
            saveAndUpload(subject)
            registerEvent(subject)
        }

        return AppEnrolResponse(subject.subjectId)
    }

    private fun getFaceCaptureResponse(results: List<Step.Result?>): FaceCaptureResponse? =
        results.filterIsInstance<FaceCaptureResponse>().lastOrNull()

    private fun getFingerprintCaptureResponse(results: List<Step.Result?>): FingerprintCaptureResponse? =
        results.filterIsInstance<FingerprintCaptureResponse>().lastOrNull()

    object SubjectBuilder {
        fun buildSubject(request: AppEnrolRequest,
                         fingerprintResponse: FingerprintCaptureResponse?,
                         faceResponse: FaceCaptureResponse?,
                         timeHelper: TimeHelper): Subject {
            return when {
                fingerprintResponse != null && faceResponse != null -> {
                    buildSubjectFromFingerprintAndFace(request, fingerprintResponse, faceResponse, timeHelper)
                }

                fingerprintResponse != null -> {
                    buildSubjectFromFingerprint(request, fingerprintResponse, timeHelper)
                }

                faceResponse != null -> {
                    buildSubjectFromFace(request, faceResponse, timeHelper)
                }

                else -> throw Throwable("Invalid response. Must be either fingerprint, face or both")
            }
        }

        private fun buildSubjectFromFingerprintAndFace(request: AppEnrolRequest,
                                                       fingerprintResponse: FingerprintCaptureResponse,
                                                       faceResponse: FaceCaptureResponse,
                                                       timeHelper: TimeHelper): Subject {
            val subjectId = UUID.randomUUID().toString()
            return Subject(
                subjectId,
                request.projectId,
                request.userId,
                request.moduleId,
                createdAt = Date(timeHelper.now()),
                fingerprintSamples = extractFingerprintSamples(fingerprintResponse),
                faceSamples = extractFaceSamples(faceResponse)
            )
        }

        private fun buildSubjectFromFingerprint(request: AppEnrolRequest,
                                                fingerprintResponse: FingerprintCaptureResponse,
                                                timeHelper: TimeHelper): Subject {
            val subjectId = UUID.randomUUID().toString()
            return Subject(
                subjectId,
                request.projectId,
                request.userId,
                request.moduleId,
                createdAt = Date(timeHelper.now()),
                fingerprintSamples = extractFingerprintSamples(fingerprintResponse)
            )
        }

        private fun buildSubjectFromFace(request: AppEnrolRequest,
                                         faceResponse: FaceCaptureResponse,
                                         timeHelper: TimeHelper): Subject {
            val subjectId = UUID.randomUUID().toString()
            return Subject(
                subjectId,
                request.projectId,
                request.userId,
                request.moduleId,
                createdAt = Date(timeHelper.now()),
                faceSamples = extractFaceSamples(faceResponse)
            )
        }

        private fun extractFingerprintSamples(
            fingerprintResponse: FingerprintCaptureResponse
        ): List<FingerprintSample> {
            return fingerprintResponse.captureResult.mapNotNull { captureResult ->
                val fingerId = captureResult.identifier
                captureResult.sample?.let { sample ->
                    FingerprintSample(fingerId, sample.template, sample.templateQualityScore)
                }
            }
        }

        private fun extractFaceSamples(faceResponse: FaceCaptureResponse) =
            faceResponse.capturingResult.mapNotNull { it ->
                it.result?.let {
                    FaceSample(it.template)
                }
            }
    }
}
