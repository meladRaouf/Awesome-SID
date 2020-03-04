package com.simprints.id.secure

import com.google.android.gms.safetynet.SafetyNetClient
import com.google.gson.JsonElement
import com.simprints.core.tools.extentions.singleWithSuspend
import com.simprints.id.data.consent.LongConsentManager
import com.simprints.id.data.db.project.remote.ProjectRemoteDataSource
import com.simprints.id.data.loginInfo.LoginInfoManager
import com.simprints.id.data.prefs.PreferencesManager
import com.simprints.id.data.prefs.RemoteConfigWrapper
import com.simprints.id.data.secure.SecureLocalDbKeyProvider
import com.simprints.id.di.AppComponent
import com.simprints.id.exceptions.safe.data.db.SimprintsInternalServerException
import com.simprints.id.exceptions.safe.secure.AuthRequestInvalidCredentialsException
import com.simprints.id.secure.models.*
import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.rxkotlin.Singles
import java.io.IOException
import javax.inject.Inject

class ProjectAuthenticator(
    component: AppComponent,
    private val safetyNetClient: SafetyNetClient,
    secureApiClient: SecureApiInterface,
    private val attestationManager: AttestationManager = AttestationManager(),
    private val authenticationDataManager: AuthenticationDataManager = AuthenticationDataManager(
        secureApiClient
    )
) {

    @Inject lateinit var secureDataManager: SecureLocalDbKeyProvider
    @Inject lateinit var loginInfoManager: LoginInfoManager
    @Inject lateinit var projectRemoteDataSource: ProjectRemoteDataSource
    @Inject lateinit var signerManager: SignerManager
    @Inject lateinit var remoteConfigWrapper: RemoteConfigWrapper
    @Inject lateinit var longConsentManager: LongConsentManager
    @Inject lateinit var preferencesManager: PreferencesManager

    internal val projectSecretManager by lazy { ProjectSecretManager(loginInfoManager) }
    private val authManager = AuthManager(secureApiClient)

    init {
        component.inject(this)
    }

    /**
     * @throws IOException
     * @throws AuthRequestInvalidCredentialsException
     * @throws SimprintsInternalServerException
     * @throws com.simprints.id.exceptions.safe.secure.SafetyNetException
     */
    fun authenticate(nonceScope: NonceScope, projectSecret: String): Completable =
        createLocalDbKeyForProject(nonceScope.projectId)
            .prepareAuthRequestParameters(nonceScope, projectSecret)
            .makeAuthRequest()
            .signIn(nonceScope.projectId, nonceScope.userId)
            .fetchProjectRemoteConfigSettings(nonceScope.projectId)
            .storeProjectRemoteConfigSettingsAndReturnProjectLanguages()
            .fetchProjectLongConsentTexts()

    private fun Completable.prepareAuthRequestParameters(
        nonceScope: NonceScope,
        projectSecret: String
    ): Single<AuthRequest> =
        andThen(buildAuthRequestParameters(nonceScope, projectSecret))

    private fun buildAuthRequestParameters(
        nonceScope: NonceScope,
        projectSecret: String
    ): Single<AuthRequest> = getAuthenticationData(
        nonceScope.projectId,
        nonceScope.userId
    ).flatMap { authenticationData ->
        zipAuthRequestParameters(
            getEncryptedProjectSecret(projectSecret, authenticationData),
            getGoogleAttestation(safetyNetClient, authenticationData),
            nonceScope
        )
    }

    internal fun getAuthenticationData(projectId: String, userId: String) =
        authenticationDataManager.requestAuthenticationData(projectId, userId)

    private fun getEncryptedProjectSecret(
        projectSecret: String,
        authenticationData: AuthenticationData
    ): Single<String> = Single.just(
        projectSecretManager.encryptAndStoreAndReturnProjectSecret(
            projectSecret,
            authenticationData.publicKeyString
        )
    )

    private fun getGoogleAttestation(
        safetyNetClient: SafetyNetClient,
        authenticationData: AuthenticationData
    ): Single<AttestToken> =
        attestationManager.requestAttestation(safetyNetClient, authenticationData.nonce)

    private fun zipAuthRequestParameters(
        encryptedProjectSecretSingle: Single<String>,
        googleAttestationSingle: Single<AttestToken>,
        nonceScope: NonceScope
    ): Single<AuthRequest> = Singles.zip(
        encryptedProjectSecretSingle,
        googleAttestationSingle
    ) { encryptedProjectSecret: String, googleAttestation: AttestToken ->
        AuthRequest(
            nonceScope.projectId,
            nonceScope.userId,
            AuthRequestBody(encryptedProjectSecret, googleAttestation.value)
        )
    }

    private fun Single<out AuthRequest>.makeAuthRequest(): Single<Token> = flatMap { authRequest ->
        authManager.requestAuthToken(authRequest)
    }

    private fun Single<out Token>.signIn(projectId: String, userId: String): Completable =
        flatMapCompletable { tokens ->
            signerManager.signIn(projectId, userId, tokens)
        }

    private fun createLocalDbKeyForProject(projectId: String) = Completable.fromAction {
        secureDataManager.setLocalDatabaseKey(projectId)
    }

    private fun Completable.fetchProjectRemoteConfigSettings(
        projectId: String
    ): Single<JsonElement> = andThen(singleWithSuspend {
        projectRemoteDataSource.loadProjectRemoteConfigSettingsJsonString(
            projectId
        )
    })

    private fun Single<out JsonElement>.storeProjectRemoteConfigSettingsAndReturnProjectLanguages(
    ): Single<Array<String>> = flatMap {
        remoteConfigWrapper.projectSettingsJsonString = it.toString()
        Single.just(preferencesManager.projectLanguages)
    }

    private fun Single<out Array<String>>.fetchProjectLongConsentTexts(): Completable =
        flatMapCompletable { languages ->
            longConsentManager.downloadAllLongConsents(languages)
        }

}
