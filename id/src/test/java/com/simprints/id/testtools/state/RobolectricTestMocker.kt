package com.simprints.id.testtools.state

import android.content.SharedPreferences
import com.google.gson.JsonObject
import com.simprints.id.commontesttools.AndroidDefaultTestConstants.DEFAULT_REALM_KEY
import com.simprints.id.commontesttools.DefaultTestConstants.DEFAULT_PROJECT_ID
import com.simprints.id.commontesttools.DefaultTestConstants.DEFAULT_PROJECT_SECRET
import com.simprints.id.commontesttools.DefaultTestConstants.DEFAULT_USER_ID
import com.simprints.id.data.db.common.RemoteDbManager
import com.simprints.id.data.db.project.domain.Project
import com.simprints.id.data.db.project.local.ProjectLocalDataSource
import com.simprints.id.data.db.project.remote.ProjectRemoteDataSource
import com.simprints.id.data.loginInfo.LoginInfoManagerImpl
import com.simprints.id.data.secure.LegacyLocalDbKeyProviderImpl
import io.mockk.coEvery
import io.mockk.every
import io.reactivex.Completable
import java.math.BigInteger

object RobolectricTestMocker {

    const val SHARED_PREFS_FOR_MOCK_FIREBASE_TOKEN_VALID = "SHARED_PREFS_FOR_MOCK_FIREBASE_TOKEN_VALID"

    suspend fun mockLoadProject(projectRemoteDataSource: ProjectRemoteDataSource,
                                projectLocalDataSource: ProjectLocalDataSource): RobolectricTestMocker {

        val project = Project(DEFAULT_PROJECT_ID, "local", "",  "")
        val projectSettings: JsonObject = JsonObject().apply { addProperty("key", "value") }
        coEvery { projectLocalDataSource.load(any()) } returns project
        coEvery { projectRemoteDataSource.loadProjectFromRemote(any()) } returns project
        coEvery { projectLocalDataSource.save(any()) } returns Unit
        coEvery { projectRemoteDataSource.loadProjectRemoteConfigSettingsJsonString(any()) } returns projectSettings
        return this
    }

    fun initLogInStateMock(sharedPrefs: SharedPreferences,
                           remoteDbManagerMock: RemoteDbManager): RobolectricTestMocker {

        every { remoteDbManagerMock.isSignedIn(any(), any()) } answers {
            sharedPrefs.getBoolean(SHARED_PREFS_FOR_MOCK_FIREBASE_TOKEN_VALID, false)
        }

        coEvery { remoteDbManagerMock.getCurrentToken() } returns ""
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
        editor.putString(LegacyLocalDbKeyProviderImpl.SHARED_PREFS_KEY_FOR_REALM_KEY + projectId, if (logged) realmKey else "")
        editor.commit()
        return this
    }
}
