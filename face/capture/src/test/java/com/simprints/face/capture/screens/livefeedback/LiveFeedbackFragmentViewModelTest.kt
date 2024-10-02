package com.simprints.face.capture.screens.livefeedback

import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.RectF
import android.util.Size
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.camera.core.ImageProxy
import com.google.common.truth.Truth.assertThat
import com.simprints.core.tools.time.TimeHelper
import com.simprints.core.tools.time.Timestamp
import com.simprints.face.capture.models.FaceDetection
import com.simprints.face.capture.models.ScreenOrientation
import com.simprints.face.capture.usecases.SimpleCaptureEventReporter
import com.simprints.face.infra.basebiosdk.detection.Face
import com.simprints.face.infra.basebiosdk.detection.FaceDetector
import com.simprints.face.infra.biosdkresolver.ResolveFaceBioSdkUseCase
import com.simprints.infra.config.sync.ConfigManager
import com.simprints.testtools.common.coroutines.TestCoroutineRule
import com.simprints.testtools.common.livedata.testObserver
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.justRun
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
internal class LiveFeedbackFragmentViewModelTest {

    @get:Rule
    val rule = InstantTaskExecutorRule()

    @get:Rule
    val testCoroutineRule = TestCoroutineRule()

    @MockK
    lateinit var frameProcessor: FrameProcessor

    @MockK
    lateinit var faceDetector: FaceDetector

    @MockK
    lateinit var frame: ImageProxy

    @MockK
    lateinit var previewFrame: Bitmap

    @MockK
    lateinit var rectF: RectF

    @MockK
    lateinit var configManager: ConfigManager

    @MockK
    lateinit var eventReporter: SimpleCaptureEventReporter

    @MockK
    lateinit var timeHelper: TimeHelper

    private val previewViewSize: Size = Size(100, 100)
    private val screenOrientation = ScreenOrientation.Portrait

    private lateinit var viewModel: LiveFeedbackFragmentViewModel

    @Before
    fun setUp() {
        MockKAnnotations.init(this, relaxed = true)

        coEvery { configManager.getProjectConfiguration().face?.qualityThreshold } returns QUALITY_THRESHOLD
        every { timeHelper.now() } returnsMany (0..100L).map { Timestamp(it) }
        justRun { frameProcessor.init(any(), any()) }
        justRun { frame.close() }
        justRun { previewFrame.recycle() }
        every { frameProcessor.cropRotateFrame(frame, screenOrientation) } returns previewFrame
        val resolveFaceBioSdkUseCase = mockk<ResolveFaceBioSdkUseCase> {
            coEvery { this@mockk.invoke() } returns mockk {
                every { detector } returns faceDetector
            }
        }


        viewModel = LiveFeedbackFragmentViewModel(
            frameProcessor, resolveFaceBioSdkUseCase, configManager, eventReporter, timeHelper
        )
    }

    @Test
    fun `Process fallback image when valid face correctly but not started capture`() = runTest {
        coEvery { faceDetector.analyze(previewFrame) } returns getFace()

        viewModel.initFrameProcessor(1, 0, rectF, previewViewSize)
        viewModel.process(frame, screenOrientation)

        val currentDetection = viewModel.currentDetection.testObserver()
        assertThat(currentDetection.observedValues.last()?.status).isEqualTo(FaceDetection.Status.VALID)

        coVerify { eventReporter.addFallbackCaptureEvent(any(), any()) }
    }

    @Test
    fun `Process valid face correctly`() = runTest {
        coEvery { faceDetector.analyze(previewFrame) } returns getFace()

        viewModel.initFrameProcessor(1, 0, rectF, previewViewSize)
        viewModel.process(frame, screenOrientation)
        viewModel.startCapture()
        viewModel.process(frame, screenOrientation)

        val currentDetection = viewModel.currentDetection.testObserver()
        assertThat(currentDetection.observedValues.last()?.status).isEqualTo(FaceDetection.Status.VALID_CAPTURING)

        coVerify { eventReporter.addCaptureEvents(any(), any(), any()) }
    }

    @Test
    fun `Process bad quality face correctly`() = runTest {
        coEvery { configManager.getProjectConfiguration().face?.qualityThreshold } returns 100
        coEvery { faceDetector.analyze(previewFrame) } returns getFace()

        viewModel.initFrameProcessor(1, 0, rectF, previewViewSize)
        viewModel.process(frame, screenOrientation)

        val currentDetection = viewModel.currentDetection.testObserver()
        assertThat(currentDetection.observedValues.last()?.status).isEqualTo(FaceDetection.Status.BAD_QUALITY)
        coVerify(exactly = 0) { eventReporter.addCaptureEvents(any(), any(), any()) }
    }

    @Test
    fun `Process invalid faces correctly`() = runTest {
        val smallFace: Face = getFace(Rect(0, 0, 30, 30))
        val bigFace: Face = getFace(Rect(0, 0, 80, 80))
        val yawedFace: Face = getFace(yaw = 45f)
        val rolledFace: Face = getFace(roll = 45f)
        val noFace = null

        every { frameProcessor.cropRotateFrame(frame, screenOrientation) } returns previewFrame
        every { faceDetector.analyze(previewFrame) } returnsMany listOf(
            smallFace,
            bigFace,
            yawedFace,
            rolledFace,
            noFace,
        )

        val detections = viewModel.currentDetection.testObserver()
        viewModel.initFrameProcessor(2, 0, rectF, previewViewSize)

        viewModel.process(frame, screenOrientation)
        viewModel.process(frame, screenOrientation)
        viewModel.process(frame, screenOrientation)
        viewModel.process(frame, screenOrientation)
        viewModel.process(frame, screenOrientation)

        detections.observedValues.let {
            assertThat(it[0]?.status).isEqualTo(FaceDetection.Status.TOOFAR)
            assertThat(it[1]?.status).isEqualTo(FaceDetection.Status.TOOCLOSE)
            assertThat(it[2]?.status).isEqualTo(FaceDetection.Status.OFFYAW)
            assertThat(it[3]?.status).isEqualTo(FaceDetection.Status.OFFROLL)
            assertThat(it[4]?.status).isEqualTo(FaceDetection.Status.NOFACE)
        }

        coVerify(exactly = 0) { eventReporter.addCaptureEvents(any(), any(), any()) }
    }

    @Test
    fun `Save all valid captures without fallback image`() = runTest {
        val validFace: Face = getFace()
        every { frameProcessor.cropRotateFrame(frame, screenOrientation) } returns previewFrame
        every { faceDetector.analyze(previewFrame) } returns validFace
        every { timeHelper.now() } returnsMany (0..100L).map { Timestamp(it) }

        val currentDetectionObserver = viewModel.currentDetection.testObserver()
        val capturingStateObserver = viewModel.capturingState.testObserver()
        viewModel.initFrameProcessor(2, 0, rectF, previewViewSize)
        viewModel.process(frame, screenOrientation)
        viewModel.startCapture()
        viewModel.process(frame, screenOrientation)
        viewModel.process(frame, screenOrientation)

        currentDetectionObserver.observedValues.let {
            assertThat(it[0]?.status).isEqualTo(FaceDetection.Status.VALID)
            assertThat(it[1]?.status).isEqualTo(FaceDetection.Status.VALID_CAPTURING)
            assertThat(it[2]?.status).isEqualTo(FaceDetection.Status.VALID_CAPTURING)
        }

        capturingStateObserver.observedValues.let {
            assertThat(it[0]).isEqualTo(LiveFeedbackFragmentViewModel.CapturingState.NOT_STARTED)
            assertThat(it[1]).isEqualTo(LiveFeedbackFragmentViewModel.CapturingState.CAPTURING)
            assertThat(it[2]).isEqualTo(LiveFeedbackFragmentViewModel.CapturingState.FINISHED)
        }

        assertThat(viewModel.userCaptures.size).isEqualTo(2)
        viewModel.userCaptures.let {
            with(it[0]) {
                assertThat(status).isEqualTo(FaceDetection.Status.VALID_CAPTURING)
                assertThat(face).isEqualTo(validFace)
                assertThat(isFallback).isEqualTo(false)
            }

            assertThat(it[1].isFallback).isEqualTo(false)
        }

        with(viewModel.sortedQualifyingCaptures) {
            assertThat(size).isEqualTo(2)
            assertThat(get(0).face).isEqualTo(validFace)
            assertThat(get(0).isFallback).isEqualTo(false)
            assertThat(get(1).face).isEqualTo(validFace)
            assertThat(get(1).isFallback).isEqualTo(false)
        }

        coVerify { eventReporter.addFallbackCaptureEvent(any(), any()) }
        coVerify(exactly = 3) { eventReporter.addCaptureEvents(any(), any(), any()) }
    }

    private fun getFace(
        rect: Rect = Rect(0, 0, 60, 60),
        quality: Float = 1f,
        yaw: Float = 0f,
        roll: Float = 0f,
    ) = Face(100, 100, rect, yaw, roll, quality, Random.nextBytes(20), "format")

    companion object {

        private const val QUALITY_THRESHOLD = -1
    }
}
