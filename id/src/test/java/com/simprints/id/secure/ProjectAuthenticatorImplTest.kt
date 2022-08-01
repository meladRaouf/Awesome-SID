package com.simprints.id.secure

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simprints.core.tools.utils.LanguageHelper
import com.simprints.id.data.consent.longconsent.LongConsentRepository
import com.simprints.id.data.db.project.ProjectRepository
import com.simprints.id.data.prefs.IdPreferencesManager
import com.simprints.id.secure.models.NonceScope
import com.simprints.infra.login.LoginManager
import com.simprints.infra.login.domain.models.AuthenticationData
import com.simprints.infra.login.domain.models.Token
import com.simprints.infra.login.exceptions.SafetyNetException
import com.simprints.infra.network.exceptions.BackendMaintenanceException
import com.simprints.infra.security.keyprovider.SecureLocalDbKeyProvider
import com.simprints.testtools.common.syntax.assertThrows
import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class ProjectAuthenticatorImplTest {

    @MockK
    private lateinit var projectRepository: ProjectRepository

    @MockK
    private lateinit var longConsentRepositoryMock: LongConsentRepository

    @MockK
    private lateinit var secureDataManager: SecureLocalDbKeyProvider

    @MockK
    private lateinit var signerManager: SignerManager

    @MockK
    private lateinit var preferencesManagerMock: IdPreferencesManager

    @MockK
    private lateinit var loginManager: LoginManager

    private lateinit var authenticator: ProjectAuthenticator

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        mockManagers()

        authenticator = buildProjectAuthenticator()
    }

    @Test
    fun successfulResponse_userShouldSignIn() = runTest(StandardTestDispatcher()) {

        authenticator.authenticate(NonceScope(PROJECT_ID, USER_ID), PROJECT_SECRET, DEVICE_ID)
    }

    @Test
    fun offline_authenticationShouldThrowException() = runTest(StandardTestDispatcher()) {
        coEvery {
            loginManager.requestAuthToken(
                PROJECT_ID,
                USER_ID,
                any()
            )
        } throws IOException()

        assertThrows<IOException> {
            authenticator.authenticate(NonceScope(PROJECT_ID, USER_ID), PROJECT_SECRET, DEVICE_ID)
        }
    }

    @Test
    fun maintenance_authenticationShouldThrowMaintenanceException() =
        runTest(StandardTestDispatcher()) {
            coEvery {
                loginManager.requestAuthToken(
                    PROJECT_ID,
                    USER_ID,
                    any()
                )
            } throws BackendMaintenanceException(
                estimatedOutage = null
            )

            assertThrows<BackendMaintenanceException> {
                authenticator.authenticate(
                    NonceScope(PROJECT_ID, USER_ID),
                    PROJECT_SECRET,
                    DEVICE_ID
                )
            }
        }

    @Test
    fun authenticate_invokeAuthenticationDataManagerCorrectly() =
        runTest(StandardTestDispatcher()) {

            authenticator.authenticate(NonceScope(PROJECT_ID, USER_ID), PROJECT_SECRET, DEVICE_ID)

            coVerify(exactly = 1) {
                loginManager.requestAuthenticationData(
                    PROJECT_ID,
                    USER_ID,
                    DEVICE_ID,
                )
            }
        }

    @Test
    fun authenticate_invokeSignerManagerCorrectly() = runTest(StandardTestDispatcher()) {

        authenticator.authenticate(NonceScope(PROJECT_ID, USER_ID), PROJECT_SECRET, DEVICE_ID)

        coVerify(exactly = 1) { signerManager.signIn(PROJECT_ID, USER_ID, any()) }
    }

    @Test
    fun authenticate_invokeSecureDataManagerCorrectly() = runTest(StandardTestDispatcher()) {

        authenticator.authenticate(NonceScope(PROJECT_ID, USER_ID), PROJECT_SECRET, DEVICE_ID)

        coVerify(exactly = 1) { secureDataManager.createLocalDatabaseKeyIfMissing(PROJECT_ID) }
    }


    @Test
    fun safetyNetFailed_shouldThrowRightException() = runTest(StandardTestDispatcher()) {
        every { loginManager.requestAttestation(any()) } throws SafetyNetException(
            "",
            SafetyNetException.SafetyNetExceptionReason.SERVICE_UNAVAILABLE
        )

        assertThrows<SafetyNetException> {
            authenticator.authenticate(NonceScope(PROJECT_ID, USER_ID), PROJECT_SECRET, DEVICE_ID)
        }
    }

    private fun buildProjectAuthenticator(): ProjectAuthenticatorImpl {
        return ProjectAuthenticatorImpl(
            loginManager,
            secureDataManager,
            projectRepository,
            signerManager,
            longConsentRepositoryMock,
            preferencesManagerMock,
        )
    }

    private fun mockManagers() {
        coEvery {
            loginManager.requestAuthenticationData(
                any(),
                any(),
                any()
            )
        } returns AuthenticationData(
            PUBLIC_KEY,
            ""
        )
        every { preferencesManagerMock.projectLanguages } returns emptyArray()
        coEvery {
            loginManager.requestAuthToken(
                PROJECT_ID,
                USER_ID,
                any()
            )
        } returns Token("", "", "", "")
        coEvery { projectRepository.fetchProjectConfigurationAndSave(any()) } returns mockk()
        every { preferencesManagerMock.projectLanguages } returns emptyArray()
        every { loginManager.requestAttestation(any()) } returns "google_attestation"
        LanguageHelper.prefs = mockk(relaxed = true)
    }

    private companion object {
        private const val PUBLIC_KEY =
            "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCAmxhSp1nSNOkRianJtMEP6uEznURRKeLmnr5q/KJnMosVeSHCtFlsDeNrjaR9r90sUgn1oA++ixcu3h6sG4nq4BEgDHi0aHQnZrFNq+frd002ji5sb9dUM2n6M7z8PPjMNiy7xl//qDIbSuwMz9u5G1VjovE4Ej0E9x1HLmXHRQIDAQAB"
        private const val PROJECT_ID = "project_id"
        private const val USER_ID = "user_id"
        private const val PROJECT_SECRET = "encrypted_project_secret"
        private const val DEVICE_ID = "device_id"
    }

}
