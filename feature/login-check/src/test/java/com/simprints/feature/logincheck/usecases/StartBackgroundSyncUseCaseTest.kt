package com.simprints.feature.logincheck.usecases

import com.simprints.infra.authlogic.AuthManager
import com.simprints.infra.config.store.ConfigRepository
import com.simprints.infra.config.store.models.SynchronizationConfiguration
import com.simprints.infra.config.sync.ConfigurationScheduler
import com.simprints.infra.eventsync.EventSyncManager
import com.simprints.infra.images.ImageUpSyncScheduler
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.impl.annotations.MockK
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class StartBackgroundSyncUseCaseTest {

    @MockK
    lateinit var eventSyncManager: EventSyncManager

    @MockK
    lateinit var imageUpSyncScheduler: ImageUpSyncScheduler

    @MockK
    lateinit var configScheduler: ConfigurationScheduler

    @MockK
    lateinit var configRepository: ConfigRepository

    @MockK
    lateinit var authManager: AuthManager

    private lateinit var useCase: StartBackgroundSyncUseCase

    @Before
    fun setUp() {
        MockKAnnotations.init(this, relaxed = true)

        useCase = StartBackgroundSyncUseCase(
            eventSyncManager,
            imageUpSyncScheduler,
            configScheduler,
            configRepository,
            authManager,
        )
    }

    @Test
    fun `Schedules all syncs when called`() = runTest {
        coEvery { configRepository.getProjectConfiguration().synchronization.frequency } returns SynchronizationConfiguration.Frequency.PERIODICALLY

        useCase.invoke()

        verify {
            eventSyncManager.scheduleSync()
            imageUpSyncScheduler.scheduleImageUpSync()
            configScheduler.scheduleSync()
            authManager.scheduleSecurityStateCheck()
        }
    }

    @Test
    fun `Starts event sync on start if required`() = runTest {
        coEvery { configRepository.getProjectConfiguration().synchronization.frequency } returns SynchronizationConfiguration.Frequency.PERIODICALLY_AND_ON_SESSION_START

        useCase.invoke()

        verify { eventSyncManager.sync() }
    }

    @Test
    fun `Does not start event sync on start if not required`() = runTest {
        coEvery { configRepository.getProjectConfiguration().synchronization.frequency } returns SynchronizationConfiguration.Frequency.PERIODICALLY

        useCase.invoke()

        verify(exactly = 0) { eventSyncManager.sync() }
    }

}
