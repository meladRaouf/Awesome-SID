package com.simprints.id.data.db

import com.simprints.id.data.analytics.eventdata.controllers.domain.SessionEventsManager
import com.simprints.id.data.analytics.eventdata.models.domain.events.EnrolmentEvent
import com.simprints.id.data.db.common.RemoteDbManager
import com.simprints.id.data.db.person.domain.PeopleCount
import com.simprints.id.data.db.person.domain.Person
import com.simprints.id.data.db.person.local.PersonLocalDataSource
import com.simprints.id.data.db.person.remote.PersonRemoteDataSource
import com.simprints.id.data.db.project.domain.Project
import com.simprints.id.data.db.project.local.ProjectLocalDataSource
import com.simprints.id.data.db.project.remote.RemoteProjectManager
import com.simprints.id.data.db.syncstatus.SyncStatusDatabase
import com.simprints.id.data.loginInfo.LoginInfoManager
import com.simprints.id.data.prefs.PreferencesManager
import com.simprints.id.secure.models.Token
import com.simprints.id.services.scheduledSync.peopleDownSync.models.SyncScope
import com.simprints.id.services.scheduledSync.peopleUpsync.PeopleUpSyncMaster
import com.simprints.id.tools.TimeHelper
import com.simprints.id.tools.extensions.trace
import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

open class DbManagerImpl(override var personLocalDataSource: PersonLocalDataSource,
                         override var projectLocalDataSource: ProjectLocalDataSource,
                         override val remote: RemoteDbManager,
                         private val loginInfoManager: LoginInfoManager,
                         private val preferencesManager: PreferencesManager,
                         private val sessionEventsManager: SessionEventsManager,
                         override val personRemoteDataSource: PersonRemoteDataSource,
                         override val remoteProjectManager: RemoteProjectManager,
                         private val timeHelper: TimeHelper,
                         private val peopleUpSyncMaster: PeopleUpSyncMaster,
                         private val syncStatusDatabase: SyncStatusDatabase) : DbManager {

    override fun signIn(projectId: String, userId: String, token: Token): Completable =
        remote.signInToRemoteDb(token.value)
            .andThen(storeCredentials(userId, projectId))
            .andThen(refreshProjectInfoWithServer(projectId).ignoreElement())
            .andThen(resumePeopleUpSync(projectId, userId))
            .trace("signInToRemoteDb")

    private fun storeCredentials(userId: String, projectId: String) =
        Completable.fromAction {
            loginInfoManager.storeCredentials(projectId, userId)
        }

    @Suppress("UNUSED_PARAMETER")
    private fun resumePeopleUpSync(projectId: String, userId: String): Completable =
        Completable.fromAction {
            peopleUpSyncMaster.resume(projectId/*, userId*/) // TODO: uncomment userId when multitenancy is properly implemented
        }

    override fun signOut() {
        //TODO: move peopleUpSyncMaster to SyncScheduler and call .pause in CheckLoginPresenter.checkSignedInOrThrow
        //If you user clears the data (then doesn't call signout), workers still stay scheduled.
        peopleUpSyncMaster.pause(loginInfoManager.signedInProjectId/*, loginInfoManager.signedInUserId*/) // TODO: uncomment userId when multitenancy is properly implemented
        loginInfoManager.cleanCredentials()
        remote.signOutOfRemoteDb()
        syncStatusDatabase.downSyncDao.deleteAll()
        syncStatusDatabase.upSyncDao.deleteAll()
        preferencesManager.clearAllSharedPreferencesExceptRealmKeys()
    }

    override fun savePerson(person: Person): Completable =
        Completable.fromCallable { runBlocking { personLocalDataSource.count(PersonLocalDataSource.Query(toSync = true)) } }
            .doOnComplete {
                sessionEventsManager
                    .updateSession {
                        it.addEvent(EnrolmentEvent(
                            timeHelper.now(),
                            person.patientId
                        ))
                    }
                    .andThen(scheduleUpsync(person.projectId, person.userId))
                    .subscribeOn(Schedulers.io())
                    .subscribeBy(onComplete = {}, onError = {
                        it.printStackTrace()
                    })
            }

    @Suppress("UNUSED_PARAMETER")
    private fun scheduleUpsync(projectId: String, userId: String): Completable = Completable.fromAction {
        peopleUpSyncMaster.schedule(projectId/*, userId*/) // TODO: uncomment userId when multitenancy is properly implemented
    }

    override fun loadPerson(projectId: String, guid: String): Single<PersonFetchResult> =
        Single.create<Person> {
            GlobalScope.launch {
                try {
                    it.onSuccess(personLocalDataSource.load(PersonLocalDataSource.Query(toSync = true)).first())
                } catch (t: Throwable) {
                    t.printStackTrace()
                    it.onError(t)
                }
            }
        }
            .map { PersonFetchResult(it, false) }
            .onErrorResumeNext {
                personRemoteDataSource
                    .downloadPerson(guid, projectId)
                    .map { person ->
                        PersonFetchResult(person, true)
                    }
            }

    override fun loadPeople(projectId: String, userId: String?, moduleId: String?): Single<List<Person>> =
        Single.create {
            GlobalScope.launch {
                try {
                    it.onSuccess(personLocalDataSource.load(PersonLocalDataSource.Query(projectId, userId = userId, moduleId = moduleId)).toList())
                } catch (t: Throwable) {
                    t.printStackTrace()
                    it.onError(t)
                }
            }
        }

    override fun loadProject(projectId: String): Single<Project> =
        Single.just(projectLocalDataSource.load(projectId))
            .doAfterSuccess {
                refreshProjectInfoWithServer(projectId)
            }
            .onErrorResumeNext {
                refreshProjectInfoWithServer(projectId)
            }

    override fun refreshProjectInfoWithServer(projectId: String): Single<Project> =
        remoteProjectManager.loadProjectFromRemote(projectId).flatMap {
            Completable.fromAction { projectLocalDataSource.save(it) }
                .andThen(Single.just(it))
        }.trace("refreshProjectInfoWithServer")
}
