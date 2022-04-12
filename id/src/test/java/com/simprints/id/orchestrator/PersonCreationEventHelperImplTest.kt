package com.simprints.id.orchestrator

import com.simprints.core.domain.face.FaceSample
import com.simprints.core.domain.face.uniqueId
import com.simprints.core.domain.fingerprint.FingerprintSample
import com.simprints.core.domain.fingerprint.uniqueId
import com.simprints.core.tools.time.TimeHelper
import com.simprints.eventsystem.event.domain.models.PersonCreationEvent
import com.simprints.eventsystem.event.domain.models.face.FaceTemplateFormat
import com.simprints.eventsystem.event.domain.models.fingerprint.FingerprintTemplateFormat
import com.simprints.eventsystem.sampledata.SampleDefaults.CREATED_AT
import com.simprints.eventsystem.sampledata.createFaceCaptureEventV3
import com.simprints.eventsystem.sampledata.createFingerprintCaptureEventV3
import com.simprints.eventsystem.sampledata.createPersonCreationEvent
import com.simprints.eventsystem.sampledata.createSessionCaptureEvent
import com.simprints.id.data.db.subject.domain.FingerIdentifier
import com.simprints.id.data.db.subject.domain.fromDomainToModuleApi
import com.simprints.id.domain.moduleapi.face.responses.FaceCaptureResponse
import com.simprints.id.domain.moduleapi.face.responses.entities.FaceCaptureResult
import com.simprints.id.domain.moduleapi.face.responses.entities.FaceCaptureSample
import com.simprints.id.domain.moduleapi.fingerprint.responses.FingerprintCaptureResponse
import com.simprints.id.domain.moduleapi.fingerprint.responses.entities.FingerprintCaptureResult
import com.simprints.id.domain.moduleapi.fingerprint.responses.entities.FingerprintCaptureSample
import com.simprints.testtools.unit.EncodingUtilsImplForTests
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test

class PersonCreationEventHelperImplTest {

    private val currentSession = createSessionCaptureEvent()

    private val fingerprintCaptureEvent =
        createFingerprintCaptureEventV3().let { it.copy(labels = it.labels.copy(sessionId = currentSession.id)) }

    private val faceCaptureEvent =
        createFaceCaptureEventV3().let { it.copy(labels = it.labels.copy(sessionId = currentSession.id)) }

    private val fingerprintSample = FingerprintCaptureSample(
        FingerIdentifier.LEFT_THUMB,
        templateQualityScore = 10,
        template = EncodingUtilsImplForTests.base64ToBytes(
            "sometemplate"
        ),
        format = FingerprintTemplateFormat.ISO_19794_2
    )

    private val faceSample = FaceCaptureSample(
        "face_id",
        EncodingUtilsImplForTests.base64ToBytes("template"),
        null,
        FaceTemplateFormat.RANK_ONE_1_23
    )

    private val fingerprintCaptureResponse = FingerprintCaptureResponse(
        captureResult = listOf(
            FingerprintCaptureResult(
                FingerIdentifier.LEFT_THUMB,
                fingerprintSample
            )
        )
    )

    private val faceCaptureResponse = FaceCaptureResponse(
        capturingResult = listOf(
            FaceCaptureResult(
                0,
                faceSample
            )
        )
    )

    @MockK
    lateinit var eventRepository: com.simprints.eventsystem.event.EventRepository

    @MockK
    lateinit var timeHelper: TimeHelper

    lateinit var personCreationEventHelper: PersonCreationEventHelper

    @Before
    fun setup() {
        MockKAnnotations.init(this, relaxed = true)

        coEvery { eventRepository.getCurrentCaptureSessionEvent() } returns createSessionCaptureEvent()
        coEvery { eventRepository.getEventsFromSession(any()) } returns emptyFlow()
        coEvery { timeHelper.now() } returns CREATED_AT


        personCreationEventHelper = PersonCreationEventHelperImpl(eventRepository, timeHelper)
    }

    @Test
    fun addPersonCreationEventIfNeeded_shouldLoadEventsForCurrentSessions() {
        runBlocking {
            coEvery { eventRepository.getEventsFromSession(any()) } returns emptyFlow()

            personCreationEventHelper.addPersonCreationEventIfNeeded(emptyList())

            coVerify(atLeast = 2) { eventRepository.getEventsFromSession(currentSession.id) }
        }
    }

    @Test
    fun fingerprintsCapturing_personCreationEventShouldHaveFingerprintsFieldsSet() {
        runBlocking {
            coEvery { eventRepository.getEventsFromSession(any()) } returns flowOf(
                currentSession,
                fingerprintCaptureEvent
            )

            personCreationEventHelper.addPersonCreationEventIfNeeded(
                listOf(
                    fingerprintCaptureResponse
                )
            )

            coVerify {
                eventRepository.addOrUpdateEvent(match {
                    it == PersonCreationEvent(
                        startTime = CREATED_AT,
                        fingerprintCaptureIds = listOf(fingerprintCaptureEvent.id),
                        fingerprintReferenceId = listOf(
                            FingerprintSample(
                                fingerprintSample.fingerIdentifier.fromDomainToModuleApi(),
                                fingerprintSample.template,
                                fingerprintSample.templateQualityScore,
                                fingerprintSample.format.fromDomainToModuleApi()
                            )
                        ).uniqueId(),
                        faceCaptureIds = null,
                        faceReferenceId = null
                    ).copy(id = it.id)
                })
            }
        }
    }

    @Test
    fun facesCapturing_personCreationEventShouldHaveFacesFieldsSet() {
        runBlocking {
            coEvery { eventRepository.getEventsFromSession(any()) } returns flowOf(
                currentSession,
                faceCaptureEvent
            )

            personCreationEventHelper.addPersonCreationEventIfNeeded(listOf(faceCaptureResponse))

            coVerify {
                eventRepository.addOrUpdateEvent(match {
                    it == PersonCreationEvent(
                        startTime = CREATED_AT,
                        fingerprintCaptureIds = null,
                        fingerprintReferenceId = null,
                        faceCaptureIds = listOf(faceCaptureEvent.id),
                        faceReferenceId = listOf(
                            FaceSample(
                                faceSample.template,
                                faceSample.format.fromDomainToModuleApi()
                            )
                        ).uniqueId()
                    ).copy(id = it.id)
                })
            }
        }
    }

    @Test
    fun facesAndFingerprintsCapturing_personCreationEventShouldHaveFacesAndFingerprintsFieldsSet() {
        runBlocking {
            coEvery { eventRepository.getEventsFromSession(any()) } returns flowOf(
                currentSession,
                fingerprintCaptureEvent,
                faceCaptureEvent
            )

            personCreationEventHelper.addPersonCreationEventIfNeeded(
                listOf(
                    fingerprintCaptureResponse,
                    faceCaptureResponse
                )
            )

            coVerify {
                eventRepository.addOrUpdateEvent(match {
                    it == PersonCreationEvent(
                        startTime = CREATED_AT,
                        fingerprintCaptureIds = listOf(fingerprintCaptureEvent.id),
                        fingerprintReferenceId = listOf(
                            FingerprintSample(
                                fingerprintSample.fingerIdentifier.fromDomainToModuleApi(),
                                fingerprintSample.template,
                                fingerprintSample.templateQualityScore,
                                fingerprintSample.format.fromDomainToModuleApi()
                            )
                        ).uniqueId(),
                        faceCaptureIds = listOf(faceCaptureEvent.id),
                        faceReferenceId = listOf(
                            FaceSample(
                                faceSample.template,
                                faceSample.format.fromDomainToModuleApi()
                            )
                        ).uniqueId()
                    ).copy(id = it.id)
                })
            }
        }
    }

    @Test
    fun personCreationEventAlreadyExistsInCurrentSession_nothingHappens() {
        runBlocking {
            coEvery { eventRepository.getEventsFromSession(any()) } returns flowOf(
                currentSession,
                fingerprintCaptureEvent,
                faceCaptureEvent,
                createPersonCreationEvent()
            )

            personCreationEventHelper.addPersonCreationEventIfNeeded(
                listOf(
                    fingerprintCaptureResponse,
                    faceCaptureResponse
                )
            )

            coVerify(exactly = 0) {
                eventRepository.addOrUpdateEvent(any())
            }
        }
    }
}
