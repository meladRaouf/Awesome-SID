package com.simprints.id.testtools.di

import com.simprints.core.tools.json.JsonHelper
import com.simprints.core.tools.time.TimeHelper
import com.simprints.eventsystem.event.EventRepository
import com.simprints.id.activities.login.tools.LoginActivityHelper
import com.simprints.id.data.consent.longconsent.LongConsentRepository
import com.simprints.id.secure.AuthenticationHelper
import com.simprints.id.secure.ProjectAuthenticator
import com.simprints.id.secure.ProjectSecretManager
import com.simprints.id.secure.SignerManager
import com.simprints.id.secure.securitystate.local.SecurityStateLocalDataSource
import com.simprints.id.secure.securitystate.remote.SecurityStateRemoteDataSource
import com.simprints.id.secure.securitystate.repository.SecurityStateRepository
import com.simprints.id.services.securitystate.SecurityStateScheduler
import com.simprints.id.services.sync.SyncManager
import com.simprints.id.services.sync.events.master.EventSyncManager
import com.simprints.infra.config.ConfigManager
import com.simprints.infra.login.LoginManager
import com.simprints.infra.network.SimNetwork
import com.simprints.infra.security.SecurityManager
import com.simprints.testtools.common.di.DependencyRule
import com.simprints.testtools.common.di.DependencyRule.RealRule
import kotlinx.coroutines.ExperimentalCoroutinesApi

class TestSecurityModule(
    private val loginActivityHelperRule: DependencyRule = RealRule,
    private val projectAuthenticatorRule: DependencyRule = RealRule,
    private val authenticationHelperRule: DependencyRule = RealRule,
    private val signerManagerRule: DependencyRule = RealRule,
    private val securityStateRepositoryRule: DependencyRule = RealRule
) : SecurityModule() {

    override fun provideSignerManager(
        configManager: ConfigManager,
        loginManager: LoginManager,
        eventSyncManager: EventSyncManager,
        syncManager: SyncManager,
        securityStateScheduler: SecurityStateScheduler,
        longConsentRepository: LongConsentRepository,
        eventRepository: EventRepository,
        simNetwork: SimNetwork,
    ): SignerManager = signerManagerRule.resolveDependency {
        super.provideSignerManager(
            configManager,
            loginManager,
            eventSyncManager,
            syncManager,
            securityStateScheduler,
            longConsentRepository,
            eventRepository,
            simNetwork,
        )
    }

    override fun provideLoginActivityHelper(
        securityStateRepository: SecurityStateRepository,
        jsonHelper: JsonHelper
    ): LoginActivityHelper {
        return loginActivityHelperRule.resolveDependency {
            super.provideLoginActivityHelper(securityStateRepository, jsonHelper)
        }
    }

    override fun provideProjectAuthenticator(
        loginManager: LoginManager,
        projectSecretManager: ProjectSecretManager,
        secureDataManager: SecurityManager,
        configManager: ConfigManager,
        signerManager: SignerManager,
        longConsentRepository: LongConsentRepository,
    ): ProjectAuthenticator {
        return projectAuthenticatorRule.resolveDependency {
            super.provideProjectAuthenticator(
                loginManager,
                projectSecretManager,
                secureDataManager,
                configManager,
                signerManager,
                longConsentRepository,
            )
        }
    }

    override fun provideAuthenticationHelper(
        loginManager: LoginManager,
        timeHelper: TimeHelper,
        projectAuthenticator: ProjectAuthenticator,
        eventRepository: EventRepository
    ): AuthenticationHelper {
        return authenticationHelperRule.resolveDependency {
            super.provideAuthenticationHelper(
                loginManager,
                timeHelper,
                projectAuthenticator,
                eventRepository
            )
        }
    }


    @ExperimentalCoroutinesApi
    override fun provideSecurityStateRepository(
        remoteDataSource: SecurityStateRemoteDataSource,
        localDataSource: SecurityStateLocalDataSource
    ): SecurityStateRepository = securityStateRepositoryRule.resolveDependency {
        super.provideSecurityStateRepository(remoteDataSource, localDataSource)
    }

}
