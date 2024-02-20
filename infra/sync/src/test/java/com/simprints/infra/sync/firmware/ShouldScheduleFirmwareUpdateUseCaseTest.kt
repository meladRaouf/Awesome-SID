package com.simprints.infra.sync.firmware

import com.google.common.truth.Truth.assertThat
import com.simprints.infra.config.store.ConfigRepository
import com.simprints.infra.config.store.models.FingerprintConfiguration
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class ShouldScheduleFirmwareUpdateUseCaseTest {

    @MockK
    private lateinit var configRepository: ConfigRepository

    private lateinit var useCase: ShouldScheduleFirmwareUpdateUseCase

    @Before
    fun setup() {
        MockKAnnotations.init(this, relaxed = true)
        useCase = ShouldScheduleFirmwareUpdateUseCase(configRepository)
    }

    @Test
    fun `should return true if Vero 2 is allowed`() = runTest {
        coEvery {
            configRepository.getProjectConfiguration().fingerprint?.allowedScanners
        } returns listOf(FingerprintConfiguration.VeroGeneration.VERO_2)

        assertThat(useCase()).isTrue()
    }

    @Test
    fun `should return false if only Vero 1 is allowed`() = runTest {
        coEvery {
            configRepository.getProjectConfiguration().fingerprint?.allowedScanners
        } returns listOf(FingerprintConfiguration.VeroGeneration.VERO_1)

        assertThat(useCase()).isFalse()
    }

    @Test
    fun `should return false if no fingerprint config`() = runTest {
        coEvery { configRepository.getProjectConfiguration().fingerprint } returns null

        assertThat(useCase()).isFalse()
    }

}
