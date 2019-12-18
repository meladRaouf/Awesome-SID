package com.simprints.id.secure

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.gms.safetynet.SafetyNet
import com.google.common.truth.Truth
import com.nhaarman.mockitokotlin2.any
import com.simprints.core.network.SimApiClient
import com.simprints.core.tools.json.JsonHelper
import com.simprints.id.commontesttools.di.TestAppModule
import com.simprints.id.commontesttools.di.TestDataModule
import com.simprints.id.commontesttools.state.setupFakeKeyStore
import com.simprints.id.data.analytics.eventdata.controllers.remote.RemoteSessionsManager
import com.simprints.id.data.db.common.RemoteDbManager
import com.simprints.id.data.db.project.local.ProjectLocalDataSource
import com.simprints.id.data.db.project.remote.ProjectRemoteDataSource
import com.simprints.id.data.prefs.PreferencesManagerImpl
import com.simprints.id.data.secure.keystore.KeystoreManager
import com.simprints.id.exceptions.safe.secure.SafetyNetException
import com.simprints.id.exceptions.safe.secure.SafetyNetExceptionReason
import com.simprints.id.secure.models.AttestToken
import com.simprints.id.secure.models.NonceScope
import com.simprints.id.secure.models.remote.ApiAuthenticationData
import com.simprints.id.secure.models.remote.ApiToken
import com.simprints.id.services.scheduledSync.people.master.PeopleSyncManager
import com.simprints.id.testtools.TestApplication
import com.simprints.id.testtools.UnitTestConfig
import com.simprints.id.testtools.state.RobolectricTestMocker
import com.simprints.testtools.common.di.DependencyRule.MockRule
import com.simprints.testtools.common.di.DependencyRule.ReplaceRule
import com.simprints.testtools.common.retrofit.createMockBehaviorService
import com.simprints.testtools.common.syntax.*
import com.simprints.testtools.unit.robolectric.ShadowAndroidXMultiDex
import com.simprints.testtools.unit.robolectric.getSharedPreferences
import io.reactivex.Single
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers
import org.robolectric.annotation.Config
import java.io.IOException
import javax.inject.Inject

@RunWith(AndroidJUnit4::class)
@Config(application = TestApplication::class, shadows = [ShadowAndroidXMultiDex::class])
class ProjectAuthenticatorTest {

    private val app = ApplicationProvider.getApplicationContext() as TestApplication

    private lateinit var apiClient: SimApiClient<SecureApiInterface>

    private val projectRemoteDataSourceMock: ProjectRemoteDataSource = mock()
    private val projectLocalDAtaSourceMock: ProjectLocalDataSource = mock()

    @Inject lateinit var remoteDbManagerMock: RemoteDbManager
    @Inject lateinit var remoteSessionsManagerMock: RemoteSessionsManager
    @Inject lateinit var peopleSyncManagerMock: PeopleSyncManager

    private val projectId = "project_id"
    private val userId = "user_id"

    private val module by lazy {
        TestAppModule(
            app,
            remoteDbManagerRule = MockRule,
            loginInfoManagerRule = MockRule,
            keystoreManagerRule = ReplaceRule { mock<KeystoreManager>().apply { setupFakeKeyStore(this) } }
        )
    }

    private val dataModule by lazy {
        TestDataModule(
            projectLocalDataSourceRule = ReplaceRule { projectLocalDAtaSourceMock },
            projectRemoteDataSourceRule = ReplaceRule { projectRemoteDataSourceMock }
        )
    }

    @Before
    fun setUp() {
        UnitTestConfig(this, module, dataModule = dataModule).fullSetup()

        runBlocking {
            RobolectricTestMocker
                .initLogInStateMock(getSharedPreferences(PreferencesManagerImpl.PREF_FILE_NAME), remoteDbManagerMock)
                .mockLoadProject(projectRemoteDataSourceMock, projectLocalDAtaSourceMock)

        }

        whenever(remoteSessionsManagerMock.getSessionsApiClient()).thenReturn(Single.create { it.onError(IllegalStateException()) })

        apiClient = SimApiClient(SecureApiInterface::class.java, SecureApiInterface.baseUrl)
    }

    @Test
    fun successfulResponse_userShouldSignIn() {

        val authenticator = ProjectAuthenticator(
            app.component,
            SafetyNet.getClient(app),
            SecureApiServiceMock(createMockBehaviorService(apiClient.retrofit, 0, SecureApiInterface::class.java)),
            getMockAttestationManager())

        val testObserver = authenticator
            .authenticate(NonceScope(projectId, userId), "encrypted_project_secret")
            .test()

        testObserver.awaitTerminalEvent()

        testObserver
            .assertNoErrors()
            .assertComplete()

        verifyOnce(peopleSyncManagerMock) { scheduleSync() } // TODO: uncomment userId when multitenancy is properly implemented
    }

    @Test
    fun offline_authenticationShouldThrowException() {

        val nonceScope = NonceScope(projectId, userId)

        val testObserver = ProjectAuthenticator(
            app.component,
            SafetyNet.getClient(app),
            createMockServiceToFailRequests(apiClient.retrofit))
            .authenticate(nonceScope, "encrypted_project_secret")
            .test()

        testObserver.awaitTerminalEvent()

        testObserver
            .assertError(IOException::class.java)
    }

    @Test
    fun getAuthenticationData_invokeAuthenticationDataManagerCorrectly() {
        val authenticationDataManager = mock<AuthenticationDataManager>()
        val projectAuthenticator = ProjectAuthenticator(mock(), mock(), mock(), mock(), authenticationDataManager)
        projectAuthenticator.getAuthenticationData(projectId, userId)

        verifyOnce(authenticationDataManager) { requestAuthenticationData(projectId, userId) }
    }

    @Test
    fun authenticate_shouldAuthenticateWithRightRequestsToSecureApiInterface() {

        val mockWebServer = MockWebServer()
        val mockProjectSecretManager: ProjectSecretManager = mock()

        whenever {
            mockProjectSecretManager.encryptAndStoreAndReturnProjectSecret(ArgumentMatchers.anyString(), anyNotNull())
        } thenReturn "encrypted_project_secret"

        mockWebServer.enqueue(mockResponseForAuthenticationData())
        mockWebServer.enqueue(mockResponseForApiToken())

        val authenticator = spy(ProjectAuthenticator(
            app.component,
            SafetyNet.getClient(app),
            SimApiClient(SecureApiInterface::class.java, mockWebServer.url("/").toString()).api,
            getMockAttestationManager()))

        whenever { authenticator.projectSecretManager } thenReturn mockProjectSecretManager

        val testObserver = authenticator.authenticate(NonceScope(projectId, userId), "encrypted_project_secret").test()

        testObserver.awaitTerminalEvent()
        testObserver.assertNoErrors()
        Truth.assertThat(mockWebServer.requestCount).isEqualTo(2)
    }

    @Test
    fun safetyNetFailed_shouldThrowRightException() {
        val attestationManager = mock<AttestationManager>()
        val nonceScope = NonceScope(projectId, userId)

        whenever {
            attestationManager.requestAttestation(any(), any())
        }  thenThrow(SafetyNetException(reason = SafetyNetExceptionReason.SERVICE_UNAVAILABLE))

        val testObserver = ProjectAuthenticator(
            app.component,
            SafetyNet.getClient(app),
            SecureApiServiceMock(createMockBehaviorService(apiClient.retrofit, 0, SecureApiInterface::class.java)),
            attestationManager)
            .authenticate(nonceScope, projectId)
            .test()

        testObserver.awaitTerminalEvent()

        testObserver
            .assertError(SafetyNetException::class.java)
    }

    private fun getMockAttestationManager(): AttestationManager {
        val attestationManager = mock<AttestationManager>()
        whenever {
            attestationManager.requestAttestation(anyNotNull(), anyNotNull())
        } thenReturn Single.just(AttestToken("google_attestation"))

        return attestationManager
    }

    private fun mockResponseForAuthenticationData() = MockResponse().let {
        it.setResponseCode(200)
        it.setBody(JsonHelper.toJson(ApiAuthenticationData("nonce", "publicKeyString")))
    }

    private fun mockResponseForApiToken() = MockResponse().let {
        it.setResponseCode(200)
        it.setBody(JsonHelper.toJson(ApiToken("firebaseCustomToken")))
    }
}
