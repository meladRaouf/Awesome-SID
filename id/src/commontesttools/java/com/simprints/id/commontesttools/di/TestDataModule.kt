package com.simprints.id.commontesttools.di

import android.content.Context
import com.simprints.id.data.db.common.RemoteDbManager
import com.simprints.id.data.db.people_sync.down.PeopleDownSyncScopeRepository
import com.simprints.id.data.db.person.PersonRepository
import com.simprints.id.data.db.person.local.PersonLocalDataSource
import com.simprints.id.data.db.person.remote.PersonRemoteDataSource
import com.simprints.id.data.db.project.ProjectRepository
import com.simprints.id.data.db.project.local.ProjectLocalDataSource
import com.simprints.id.data.db.project.remote.ProjectRemoteDataSource
import com.simprints.id.data.loginInfo.LoginInfoManager
import com.simprints.id.data.secure.SecureDataManager
import com.simprints.id.di.DataModule
import com.simprints.id.services.scheduledSync.sync.peopleUpsync.PeopleUpSyncMaster
import com.simprints.testtools.common.di.DependencyRule

class TestDataModule(private val projectLocalDataSourceRule: DependencyRule = DependencyRule.RealRule,
                     private val projectRemoteDataSourceRule: DependencyRule = DependencyRule.RealRule,
                     private val projectRepositoryRule: DependencyRule = DependencyRule.RealRule,
                     private val personLocalDataSource: DependencyRule = DependencyRule.RealRule,
                     private val personRepositoryRule: DependencyRule = DependencyRule.RealRule) : DataModule() {

    override fun provideProjectLocalDataSource(ctx: Context,
                                               secureDataManager: SecureDataManager,
                                               loginInfoManager: LoginInfoManager): ProjectLocalDataSource =
        projectLocalDataSourceRule.resolveDependency { super.provideProjectLocalDataSource(ctx, secureDataManager, loginInfoManager) }

    override fun provideProjectRemoteDataSource(remoteDbManager: RemoteDbManager): ProjectRemoteDataSource =
        projectRemoteDataSourceRule.resolveDependency { super.provideProjectRemoteDataSource(remoteDbManager) }


    override fun provideProjectRepository(projectLocalDataSource: ProjectLocalDataSource,
                                          projectRemoteDataSource: ProjectRemoteDataSource): ProjectRepository =
        projectRepositoryRule.resolveDependency { super.provideProjectRepository(projectLocalDataSource, projectRemoteDataSource) }

    override fun providePersonRepository(personLocalDataSource: PersonLocalDataSource,
                                         personRemoteDataSource: PersonRemoteDataSource,
                                         peopleUpSyncMaster: PeopleUpSyncMaster,
                                         downSyncScopeRepository: PeopleDownSyncScopeRepository): PersonRepository =
        personRepositoryRule.resolveDependency {
            super.providePersonRepository(personLocalDataSource, personRemoteDataSource, peopleUpSyncMaster, downSyncScopeRepository)
        }


    override fun providePersonLocalDataSource(ctx: Context,
                                              secureDataManager: SecureDataManager,
                                              loginInfoManager: LoginInfoManager): PersonLocalDataSource =
        personLocalDataSource.resolveDependency { super.providePersonLocalDataSource(ctx, secureDataManager, loginInfoManager) }
}
