package com.simprints.id.di

import android.content.Context
import androidx.work.WorkManager
import com.simprints.core.tools.json.JsonHelper
import com.simprints.id.data.db.event.EventRepository
import com.simprints.id.data.db.events_sync.EventsSyncStatusDatabase
import com.simprints.id.data.db.events_sync.down.EventDownSyncScopeRepository
import com.simprints.id.data.db.events_sync.down.EventDownSyncScopeRepositoryImpl
import com.simprints.id.data.db.events_sync.down.local.EventDownSyncOperationLocalDataSource
import com.simprints.id.data.db.events_sync.up.EventUpSyncScopeRepository
import com.simprints.id.data.db.events_sync.up.EventUpSyncScopeRepositoryImpl
import com.simprints.id.data.db.events_sync.up.local.EventsUpSyncOperationLocalDataSource
import com.simprints.id.data.db.subject.SubjectRepository
import com.simprints.id.data.loginInfo.LoginInfoManager
import com.simprints.id.data.prefs.PreferencesManager
import com.simprints.id.data.secure.EncryptedSharedPreferencesBuilder
import com.simprints.id.services.sync.SyncManager
import com.simprints.id.services.sync.SyncSchedulerImpl
import com.simprints.id.services.sync.events.down.EventDownSyncHelper
import com.simprints.id.services.sync.events.down.EventDownSyncHelperImpl
import com.simprints.id.services.sync.events.down.EventDownSyncWorkersBuilder
import com.simprints.id.services.sync.events.down.EventDownSyncWorkersBuilderImpl
import com.simprints.id.services.sync.events.master.EventSyncManager
import com.simprints.id.services.sync.events.master.EventSyncManagerImpl
import com.simprints.id.services.sync.events.master.EventSyncStateProcessorImpl
import com.simprints.id.services.sync.events.master.SubjectsSyncStateProcessor
import com.simprints.id.services.sync.events.master.internal.EventSyncCache
import com.simprints.id.services.sync.events.master.internal.EventSyncCache.Companion.FILENAME_FOR_LAST_SYNC_TIME_SHARED_PREFS
import com.simprints.id.services.sync.events.master.internal.EventSyncCache.Companion.FILENAME_FOR_PROGRESSES_SHARED_PREFS
import com.simprints.id.services.sync.events.master.internal.EventSyncCacheImpl
import com.simprints.id.services.sync.events.master.workers.SubjectsSyncSubMasterWorkersBuilder
import com.simprints.id.services.sync.events.master.workers.SubjectsSyncSubMasterWorkersBuilderImpl
import com.simprints.id.services.sync.events.up.EventUpSyncHelper
import com.simprints.id.services.sync.events.up.EventUpSyncHelperImpl
import com.simprints.id.services.sync.events.up.EventUpSyncWorkersBuilder
import com.simprints.id.services.sync.events.up.EventUpSyncWorkersBuilderImpl
import com.simprints.id.services.sync.images.up.ImageUpSyncScheduler
import com.simprints.id.services.sync.sessionSync.SessionEventsSyncManager
import com.simprints.id.services.sync.sessionSync.SessionEventsSyncManagerImpl
import com.simprints.id.services.sync.subjects.up.controllers.SubjectsUpSyncExecutor
import com.simprints.id.services.sync.subjects.up.controllers.SubjectsUpSyncExecutorImpl
import com.simprints.id.tools.TimeHelper
import dagger.Module
import dagger.Provides

@Module
open class SyncModule {

    @Provides
    open fun provideWorkManager(ctx: Context): WorkManager =
        WorkManager.getInstance(ctx)

    @Provides
    open fun provideSessionEventsSyncManager(workManager: WorkManager): SessionEventsSyncManager =
        SessionEventsSyncManagerImpl(workManager)

    @Provides
    open fun providePeopleSyncStateProcessor(ctx: Context,
                                             eventSyncCache: EventSyncCache,
                                             personRepository: SubjectRepository): SubjectsSyncStateProcessor =
        EventSyncStateProcessorImpl(ctx, personRepository, eventSyncCache)

    @Provides
    open fun provideEventUpSyncScopeRepo(loginInfoManager: LoginInfoManager,
                                         eventsUpSyncOperationLocalDataSource: EventsUpSyncOperationLocalDataSource
    ): EventUpSyncScopeRepository = EventUpSyncScopeRepositoryImpl(loginInfoManager, eventsUpSyncOperationLocalDataSource)

    @Provides
    open fun providePeopleSyncManager(ctx: Context,
                                      subjectsSyncStateProcessor: SubjectsSyncStateProcessor,
                                      downSyncScopeRepository: EventDownSyncScopeRepository,
                                      upSyncScopeRepo: EventUpSyncScopeRepository,
                                      eventSyncCache: EventSyncCache): EventSyncManager =
        EventSyncManagerImpl(ctx, subjectsSyncStateProcessor, downSyncScopeRepository, upSyncScopeRepo, eventSyncCache)

    @Provides
    open fun provideSyncManager(
        sessionEventsSyncManager: SessionEventsSyncManager,
        eventSyncManager: EventSyncManager,
        imageUpSyncScheduler: ImageUpSyncScheduler
    ): SyncManager = SyncSchedulerImpl(
        sessionEventsSyncManager,
        eventSyncManager,
        imageUpSyncScheduler
    )

    @Provides
    open fun provideEventDownSyncScopeRepo(
        loginInfoManager: LoginInfoManager,
        preferencesManager: PreferencesManager,
        downSyncOperationLocalDataSource: EventDownSyncOperationLocalDataSource
    ): EventDownSyncScopeRepository =
        EventDownSyncScopeRepositoryImpl(loginInfoManager, preferencesManager, downSyncOperationLocalDataSource)

    @Provides
    open fun provideDownSyncWorkerBuilder(downSyncScopeRepository: EventDownSyncScopeRepository,
                                          jsonHelper: JsonHelper): EventDownSyncWorkersBuilder =
        EventDownSyncWorkersBuilderImpl(downSyncScopeRepository, jsonHelper)


    @Provides
    open fun providePeopleUpSyncWorkerBuilder(upSyncScopeRepository: EventUpSyncScopeRepository,
                                              jsonHelper: JsonHelper): EventUpSyncWorkersBuilder =
        EventUpSyncWorkersBuilderImpl(upSyncScopeRepository, jsonHelper)

    @Provides
    open fun providePeopleUpSyncDao(database: EventsSyncStatusDatabase): EventsUpSyncOperationLocalDataSource =
        database.upSyncOperationsDao

    @Provides
    open fun providePeopleDownSyncDao(database: EventsSyncStatusDatabase): EventDownSyncOperationLocalDataSource =
        database.downSyncOperationsDao

    @Provides
    open fun providePeopleUpSyncManager(ctx: Context,
                                        subjectsUpSyncWorkersBuilder: EventUpSyncWorkersBuilder): SubjectsUpSyncExecutor =
        SubjectsUpSyncExecutorImpl(ctx, subjectsUpSyncWorkersBuilder)


    @Provides
    open fun providePeopleSyncProgressCache(builder: EncryptedSharedPreferencesBuilder): EventSyncCache =
        EventSyncCacheImpl(
            builder.buildEncryptedSharedPreferences(FILENAME_FOR_PROGRESSES_SHARED_PREFS),
            builder.buildEncryptedSharedPreferences(FILENAME_FOR_LAST_SYNC_TIME_SHARED_PREFS)
        )

    @Provides
    open fun provideEventDownSyncHelper(subjectRepository: SubjectRepository,
                                        eventRepository: EventRepository,
                                        eventDownSyncScopeRepository: EventDownSyncScopeRepository,
                                        timeHelper: TimeHelper): EventDownSyncHelper =
        EventDownSyncHelperImpl(subjectRepository, eventRepository, eventDownSyncScopeRepository, timeHelper)

    @Provides
    open fun provideEventUpSyncHelper(eventRepository: EventRepository,
                                      eventUpSyncScopeRepo: EventUpSyncScopeRepository,
                                      timerHelper: TimeHelper): EventUpSyncHelper =
        EventUpSyncHelperImpl(eventRepository, eventUpSyncScopeRepo, timerHelper)

    @Provides
    open fun providePeopleSyncSubMasterWorkersBuilder(): SubjectsSyncSubMasterWorkersBuilder =
        SubjectsSyncSubMasterWorkersBuilderImpl()
}
