package com.simprints.fingerprint.infra.necsdkimpl.acquisition.template

import com.simprints.fingerprint.infra.basebiosdk.exceptions.BioSdkException
import com.simprints.fingerprint.infra.necsdkimpl.acquisition.image.ProcessedImageCache
import com.simprints.fingerprint.infra.scanner.capture.FingerprintCaptureWrapper
import com.simprints.fingerprint.infra.scanner.capture.FingerprintCaptureWrapperFactory
import com.simprints.fingerprint.infra.scanner.v2.domain.main.message.un20.models.Dpi
import com.simprints.sgimagecorrection.SecugenImageCorrection
import io.mockk.MockKAnnotations
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class FingerprintTemplateProviderImplTest {

    private lateinit var fingerprintTemplateProviderImpl: FingerprintTemplateProviderImpl

    @RelaxedMockK
    private lateinit var calculateNecImageQualityUseCase: CalculateNecImageQualityUseCase

    @RelaxedMockK
    private lateinit var secugenImageCorrection: SecugenImageCorrection

    @RelaxedMockK
    private lateinit var fingerprintCaptureWrapperFactory: FingerprintCaptureWrapperFactory

    @RelaxedMockK
    private lateinit var extractNecTemplateUseCase: ExtractNecTemplateUseCase

    @RelaxedMockK
    private lateinit var decodeWsqImageUseCase: DecodeWSQImageUseCase

    @RelaxedMockK
    private lateinit var processedImageCache: ProcessedImageCache

    @RelaxedMockK
    private lateinit var captureWrapper: FingerprintCaptureWrapper


    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        every { fingerprintCaptureWrapperFactory.captureWrapper } returns captureWrapper

        fingerprintTemplateProviderImpl = FingerprintTemplateProviderImpl(
            fingerprintCaptureWrapperFactory = fingerprintCaptureWrapperFactory,
            decodeWSQImageUseCase = decodeWsqImageUseCase,
            secugenImageCorrection = secugenImageCorrection,
            calculateNecImageQualityUseCase = calculateNecImageQualityUseCase,
            extractNecTemplateUseCase = extractNecTemplateUseCase,
            captureProcessedImageCache = processedImageCache
        )

    }

    @Test(expected = IllegalArgumentException::class)
    fun `test null settings throws exception`() = runTest {
        // Given
        val settings = null
        // When
        fingerprintTemplateProviderImpl.acquireFingerprintTemplate(settings)
        // Then exception is thrown

    }

    @Test
    fun `test acquireFingerprintTemplate success`() = runTest {
        // Given
        val settings = FingerprintTemplateAcquisitionSettings(
            processingResolution = Dpi(500),
            qualityThreshold = 0,
            timeOutMs = 0
        )
        // When
        fingerprintTemplateProviderImpl.acquireFingerprintTemplate(settings)
        // Then
        coVerify {
            captureWrapper
                .acquireUnprocessedImage(any())
            decodeWsqImageUseCase.invoke(any())
            processedImageCache.recentlyCapturedImage = any()
            secugenImageCorrection.processRawImage(any(), any())
            calculateNecImageQualityUseCase.invoke(any())
            extractNecTemplateUseCase.invoke(any(), any())

        }
    }

    @Test(expected = BioSdkException.ImageQualityBelowThresholdException::class)
    fun `test acquireFingerprintTemplate fails if quality score is less than threshold`() =
        runTest {
            // Given
            every { calculateNecImageQualityUseCase.invoke(any()) } returns 10
            val settings = FingerprintTemplateAcquisitionSettings(
                processingResolution = Dpi(500),
                qualityThreshold = 20,
                timeOutMs = 0
            )
            // When
            fingerprintTemplateProviderImpl.acquireFingerprintTemplate(settings)
            // Then
            coVerify {
                captureWrapper
                    .acquireUnprocessedImage(any())
                decodeWsqImageUseCase.invoke(any())
                processedImageCache.recentlyCapturedImage = any()
                secugenImageCorrection.processRawImage(any(), any())
                calculateNecImageQualityUseCase.invoke(any())
            }
            coVerify(exactly = 0) {
                extractNecTemplateUseCase.invoke(any(), any())
            }

        }
}
