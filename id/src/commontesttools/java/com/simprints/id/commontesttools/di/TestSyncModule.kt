package com.simprints.id.commontesttools.di

import android.content.Context
import androidx.work.WorkManager
import com.simprints.id.data.db.subjects_sync.SubjectsSyncStatusDatabase
import com.simprints.id.data.db.subjects_sync.down.SubjectsDownSyncScopeRepository
import com.simprints.id.data.db.subjects_sync.down.domain.SubjectsDownSyncOperationFactory
import com.simprints.id.data.db.subjects_sync.down.local.SubjectsDownSyncOperationLocalDataSource
import com.simprints.id.data.db.subjects_sync.up.SubjectsUpSyncScopeRepository
import com.simprints.id.data.db.subjects_sync.up.local.SubjectsUpSyncOperationLocalDataSource
import com.simprints.id.data.db.subject.SubjectRepository
import com.simprints.id.data.loginInfo.LoginInfoManager
import com.simprints.id.data.prefs.PreferencesManager
import com.simprints.id.di.SyncModule
import com.simprints.id.services.scheduledSync.SyncManager
import com.simprints.id.services.scheduledSync.imageUpSync.ImageUpSyncScheduler
import com.simprints.id.services.scheduledSync.subjects.down.controllers.SubjectsDownSyncWorkersBuilder
import com.simprints.id.services.scheduledSync.subjects.master.SubjectsSyncManager
import com.simprints.id.services.scheduledSync.subjects.master.SubjectsSyncStateProcessor
import com.simprints.id.services.scheduledSync.subjects.master.internal.SubjectsSyncCache
import com.simprints.id.services.scheduledSync.subjects.up.controllers.SubjectsUpSyncExecutor
import com.simprints.id.services.scheduledSync.subjects.up.controllers.SubjectsUpSyncWorkersBuilder
import com.simprints.id.services.scheduledSync.sessionSync.SessionEventsSyncManager
import com.simprints.testtools.common.di.DependencyRule
import javax.inject.Singleton

class TestSyncModule(
    private val peopleDownSyncScopeRepositoryRule: DependencyRule = DependencyRule.RealRule,
    private val peopleSessionEventsSyncManager: DependencyRule = DependencyRule.RealRule,
    private val peopleSyncStateProcessor: DependencyRule = DependencyRule.RealRule,
    private val peopleSyncManagerRule: DependencyRule = DependencyRule.RealRule,
    private val syncManagerRule: DependencyRule = DependencyRule.RealRule,
    private val peopleDownSyncWorkersBuilderRule: DependencyRule = DependencyRule.RealRule,
    private val peopleUpSyncWorkersBuilderRule: DependencyRule = DependencyRule.RealRule,
    private val peopleUpSyncDaoRule: DependencyRule = DependencyRule.RealRule,
    private val peopleDownSyncDaoRule: DependencyRule = DependencyRule.RealRule,
    private val peopleUpSyncManagerRule: DependencyRule = DependencyRule.RealRule,
    private val peopleUpSyncScopeRepositoryRule: DependencyRule = DependencyRule.RealRule
) : SyncModule() {

    @Singleton
    override fun provideDownSyncScopeRepository(
        loginInfoManager: LoginInfoManager,
        preferencesManager: PreferencesManager,
        syncStatusDatabase: SubjectsSyncStatusDatabase,
        subjectsDownSyncOperationFactory: SubjectsDownSyncOperationFactory
    ): SubjectsDownSyncScopeRepository = peopleDownSyncScopeRepositoryRule.resolveDependency {
        super.provideDownSyncScopeRepository(
            loginInfoManager,
            preferencesManager,
            syncStatusDatabase,
            subjectsDownSyncOperationFactory)
    }

    @Singleton
    override fun provideSessionEventsSyncManager(workManager: WorkManager): SessionEventsSyncManager =
        peopleSessionEventsSyncManager.resolveDependency { super.provideSessionEventsSyncManager(workManager) }

    @Singleton
    override fun providePeopleSyncStateProcessor(
        ctx: Context,
        subjectsSyncCache: SubjectsSyncCache,
        personRepository: SubjectRepository
    ): SubjectsSyncStateProcessor = peopleSyncStateProcessor.resolveDependency {
        super.providePeopleSyncStateProcessor(ctx, subjectsSyncCache, personRepository)
    }

    @Singleton
    override fun providePeopleSyncManager(
        ctx: Context,
        subjectsSyncStateProcessor: SubjectsSyncStateProcessor,
        subjectsUpSyncScopeRepository: SubjectsUpSyncScopeRepository,
        subjectsDownSyncScopeRepository: SubjectsDownSyncScopeRepository,
        subjectsSyncCache: SubjectsSyncCache
    ): SubjectsSyncManager = peopleSyncManagerRule.resolveDependency {
        super.providePeopleSyncManager(ctx, subjectsSyncStateProcessor, subjectsUpSyncScopeRepository, subjectsDownSyncScopeRepository, subjectsSyncCache)
    }

    @Singleton
    override fun provideSyncManager(
        sessionEventsSyncManager: SessionEventsSyncManager,
        subjectsSyncManager: SubjectsSyncManager,
        imageUpSyncScheduler: ImageUpSyncScheduler
    ): SyncManager = syncManagerRule.resolveDependency {
        super.provideSyncManager(
            sessionEventsSyncManager,
            subjectsSyncManager,
            imageUpSyncScheduler
        )
    }

    @Singleton
    override fun provideDownSyncWorkerBuilder(
        downSyncScopeRepository: SubjectsDownSyncScopeRepository
    ): SubjectsDownSyncWorkersBuilder = peopleDownSyncWorkersBuilderRule.resolveDependency {
        super.provideDownSyncWorkerBuilder(downSyncScopeRepository)
    }

    @Singleton
    override fun providePeopleUpSyncWorkerBuilder(): SubjectsUpSyncWorkersBuilder =
        peopleUpSyncWorkersBuilderRule.resolveDependency {
            super.providePeopleUpSyncWorkerBuilder()
        }

    @Singleton
    override fun providePeopleUpSyncDao(database: SubjectsSyncStatusDatabase): SubjectsUpSyncOperationLocalDataSource =
        peopleUpSyncDaoRule.resolveDependency { super.providePeopleUpSyncDao(database) }

    @Singleton
    override fun providePeopleDownSyncDao(
        database: SubjectsSyncStatusDatabase
    ): SubjectsDownSyncOperationLocalDataSource = peopleDownSyncDaoRule.resolveDependency {
        super.providePeopleDownSyncDao(database)
    }

    @Singleton
    override fun providePeopleUpSyncManager(
        ctx: Context,
        subjectsUpSyncWorkersBuilder: SubjectsUpSyncWorkersBuilder
    ): SubjectsUpSyncExecutor = peopleUpSyncManagerRule.resolveDependency {
        super.providePeopleUpSyncManager(ctx, subjectsUpSyncWorkersBuilder)
    }

    @Singleton
    override fun provideUpSyncScopeRepository(
        loginInfoManager: LoginInfoManager,
        operationLocalDataSource: SubjectsUpSyncOperationLocalDataSource
    ): SubjectsUpSyncScopeRepository = peopleUpSyncScopeRepositoryRule.resolveDependency {
        super.provideUpSyncScopeRepository(loginInfoManager, operationLocalDataSource)
    }

}
