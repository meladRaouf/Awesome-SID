package com.simprints.id.testtools.state

import android.content.SharedPreferences
import com.google.gson.JsonObject
import com.simprints.id.commontesttools.DefaultTestConstants.DEFAULT_PROJECT_ID
import com.simprints.id.commontesttools.DefaultTestConstants.DEFAULT_PROJECT_SECRET
import com.simprints.id.commontesttools.DefaultTestConstants.DEFAULT_REALM_KEY
import com.simprints.id.commontesttools.DefaultTestConstants.DEFAULT_USER_ID
import com.simprints.id.data.analytics.eventdata.controllers.local.SessionEventsLocalDbManager
import com.simprints.id.data.db.common.RemoteDbManager
import com.simprints.id.data.db.person.remote.PeopleRemoteInterface
import com.simprints.id.data.db.project.remote.RemoteProjectManager
import com.simprints.id.data.loginInfo.LoginInfoManagerImpl
import com.simprints.id.data.secure.SecureDataManagerImpl
import com.simprints.id.data.db.project.domain.Project
import com.simprints.testtools.common.syntax.anyNotNull
import com.simprints.testtools.common.syntax.anyOrNull
import com.simprints.testtools.common.syntax.whenever
import io.reactivex.Completable
import io.reactivex.Single
import okhttp3.mockwebserver.MockWebServer
import org.mockito.stubbing.Answer
import java.math.BigInteger

object RobolectricTestMocker {

    const val SHARED_PREFS_FOR_MOCK_FIREBASE_TOKEN_VALID = "SHARED_PREFS_FOR_MOCK_FIREBASE_TOKEN_VALID"

    fun mockLoadProject(localDbManagerMock: LocalDbManager, remoteProjectManagerMock: RemoteProjectManager): RobolectricTestMocker {
        val project = Project().apply { id = "project id"; name = "project name"; description = "project desc" }
        val projectSettings: JsonObject = JsonObject().apply { addProperty("key", "value") }
        whenever { localDbManagerMock.loadProjectFromLocal(anyNotNull()) } thenReturn Single.just(project)
        whenever { remoteProjectManagerMock.loadProjectFromRemote(anyNotNull()) } thenReturn Single.just(project)
        whenever { localDbManagerMock.saveProjectIntoLocal(anyNotNull()) } thenReturn Completable.complete()
        whenever { remoteProjectManagerMock.loadProjectRemoteConfigSettingsJsonString(anyNotNull()) } thenReturn Single.just(projectSettings)
        return this
    }

    fun initLogInStateMock(sharedPrefs: SharedPreferences,
                           remoteDbManagerMock: RemoteDbManager): RobolectricTestMocker {

        val answer = Answer<Boolean> {
            sharedPrefs.getBoolean(SHARED_PREFS_FOR_MOCK_FIREBASE_TOKEN_VALID, false)
        }
        whenever { remoteDbManagerMock.isSignedIn(anyNotNull(), anyNotNull()) } thenAnswer answer
        whenever { remoteDbManagerMock.getCurrentToken() } thenReturn Single.just("")
        whenever { remoteDbManagerMock.signInToRemoteDb(anyNotNull()) } thenReturn Completable.complete()
        return this
    }

    fun setUserLogInState(logged: Boolean,
                          sharedPrefs: SharedPreferences,
                          projectId: String = DEFAULT_PROJECT_ID,
                          userId: String = DEFAULT_USER_ID,
                          projectSecret: String = DEFAULT_PROJECT_SECRET,
                          realmKey: String = BigInteger(1, DEFAULT_REALM_KEY).toString(16)): RobolectricTestMocker {

        Thread.sleep(1000)
        val editor = sharedPrefs.edit()
        editor.putString(LoginInfoManagerImpl.ENCRYPTED_PROJECT_SECRET, if (logged) projectSecret else "")
        editor.putString(LoginInfoManagerImpl.PROJECT_ID, if (logged) projectId else "")
        editor.putString(LoginInfoManagerImpl.USER_ID, if (logged) userId else "")
        editor.putBoolean(SHARED_PREFS_FOR_MOCK_FIREBASE_TOKEN_VALID, logged)
        editor.putString(SecureDataManagerImpl.SHARED_PREFS_KEY_FOR_REALM_KEY + projectId, if (logged) realmKey else "")
        editor.commit()
        return this
    }

    fun setupLocalAndRemoteManagersForApiTesting(localDbManagerSpy: LocalDbManager,
                                                 remoteDbManagerSpy: RemoteDbManager,
                                                 sessionEventsLocalDbManagerMock: SessionEventsLocalDbManager,
                                                 mockServer: MockWebServer? = null): RobolectricTestMocker {

        PeopleRemoteInterface.baseUrl = mockServer?.url("/").toString()
        whenever { localDbManagerSpy.insertOrUpdatePersonInLocal(anyNotNull()) } thenReturn Completable.complete()
        whenever { localDbManagerSpy.loadPersonFromLocal(anyNotNull()) } thenReturn Single.error(IllegalStateException())
        whenever { localDbManagerSpy.getPeopleCountFromLocal(anyOrNull(), anyNotNull(), anyNotNull(), anyNotNull(), anyNotNull()) } thenReturn Single.error(IllegalStateException())

        setupSessionEventsManagerToAvoidRealmCall(sessionEventsLocalDbManagerMock)

        whenever { remoteDbManagerSpy.getCurrentToken() } thenReturn Single.just("someToken")
        return this
    }

    fun setupSessionEventsManagerToAvoidRealmCall(sessionEventsLocalDbManagerMock: SessionEventsLocalDbManager): RobolectricTestMocker {
        whenever { sessionEventsLocalDbManagerMock.loadSessions(anyOrNull(), anyOrNull()) } thenReturn Single.error(IllegalStateException())
        whenever { sessionEventsLocalDbManagerMock.insertOrUpdateSessionEvents(anyNotNull()) } thenReturn Completable.complete()
        return this
    }
}
