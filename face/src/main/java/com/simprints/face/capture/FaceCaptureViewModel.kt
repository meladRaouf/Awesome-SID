package com.simprints.face.capture

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simprints.core.livedata.LiveDataEvent
import com.simprints.core.livedata.LiveDataEventWithContent
import com.simprints.core.livedata.send
import com.simprints.face.capture.FaceCaptureActivity.BackButtonContext
import com.simprints.face.capture.FaceCaptureActivity.BackButtonContext.*
import com.simprints.face.controllers.core.events.model.RefusalAnswer
import com.simprints.face.controllers.core.image.FaceImageManager
import com.simprints.face.data.moduleapi.face.requests.FaceCaptureRequest
import com.simprints.face.data.moduleapi.face.responses.FaceCaptureResponse
import com.simprints.face.data.moduleapi.face.responses.FaceExitFormResponse
import com.simprints.face.data.moduleapi.face.responses.entities.FaceCaptureResult
import com.simprints.face.models.FaceDetection
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class FaceCaptureViewModel(
    private val maxRetries: Int,
    private val faceImageManager: FaceImageManager
) : ViewModel() {
//    private val analyticsManager: AnalyticsManager

    var faceDetections = listOf<FaceDetection>()

    val retryFlowEvent: MutableLiveData<LiveDataEvent> = MutableLiveData()
    val recaptureEvent: MutableLiveData<LiveDataEvent> = MutableLiveData()
    val exitFormEvent: MutableLiveData<LiveDataEvent> = MutableLiveData()

    val finishFlowEvent: MutableLiveData<LiveDataEventWithContent<FaceCaptureResponse>> =
        MutableLiveData()
    val finishFlowWithExitFormEvent: MutableLiveData<LiveDataEventWithContent<FaceExitFormResponse>> =
        MutableLiveData()

    private var retriesUsed: Int = 0

    val canRetry: Boolean
        get() = retriesUsed++ < maxRetries

    var samplesToCapture = 1

    init {
        viewModelScope.launch { startNewAnalyticsSession() }
    }

    fun setupCapture(faceRequest: FaceCaptureRequest) {
        samplesToCapture = faceRequest.nFaceSamplesToCapture
    }

    fun flowFinished() {
        saveFaceDetections()
        // TODO: add analytics for FlowFinished(SUCCESS) and EndSession

        val results = faceDetections.mapIndexed { index, detection ->
            FaceCaptureResult(index, detection.toFaceSample())
        }

        finishFlowEvent.send(FaceCaptureResponse(results))
    }

    fun captureFinished(newFaceDetections: List<FaceDetection>) {
        faceDetections = newFaceDetections
    }

    fun handleBackButton(backButtonContext: BackButtonContext) {
        when (backButtonContext) {
            CAPTURE -> startExitForm()
            CONFIRMATION -> flowFinished()
            RETRY -> handleRetry(true)
        }
    }

    fun handleRetry(isBackButton: Boolean) {
        if (canRetry) {
            if (isBackButton) startExitForm() else retryFlow()
        } else {
            finishFlowWithFailedRetries()
        }
    }

    fun recapture() {
        // TODO: add analytics for FlowFinished(RECAPTURE)
        faceDetections = listOf()
        recaptureEvent.send()
    }

    private fun startExitForm() {
        exitFormEvent.send()
    }

    private fun retryFlow() {
        // TODO: add analytics for FlowFinished(RETRY)
        faceDetections = listOf()
        retryFlowEvent.send()
    }

    // TODO: should have a better understanding on what to do after failed all retries
    private fun finishFlowWithFailedRetries() {
        // TODO: add analytics for FlowFinished(RETRY_FAIL)
        flowFinished()
    }

    private fun startNewAnalyticsSession() {
        // TODO: add analytics for StartSession
    }

    private fun saveFaceDetections() {
        // TODO: send the correct captureEventId once we can get it
        faceDetections.forEachIndexed { index, faceDetection ->
            saveImage(
                faceDetection,
                index.toString()
            )
        }
    }

    private fun saveImage(faceDetection: FaceDetection, captureEventId: String) {
        runBlocking {
            faceDetection.securedImageRef =
                faceImageManager.save(faceDetection.frame.toByteArray(), captureEventId)
        }
    }

    fun submitExitForm(reason: RefusalAnswer, exitFormText: String) {
        finishFlowWithExitFormEvent.send(FaceExitFormResponse(reason, exitFormText))
    }

}
