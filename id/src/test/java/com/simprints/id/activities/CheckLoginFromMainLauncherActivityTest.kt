package com.simprints.id.activities

import android.app.Activity
import android.content.SharedPreferences
import com.google.firebase.FirebaseApp
import com.simprints.id.Application
import com.simprints.id.BuildConfig
import com.simprints.id.activities.dashboard.DashboardActivity
import com.simprints.id.activities.requestLogin.RequestLoginActivity
import com.simprints.id.data.prefs.loginInfo.LoginInfoManagerImpl
import shared.assertActivityStarted
import com.simprints.id.testUtils.roboletric.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(constants = BuildConfig::class, application = TestApplication::class)
class CheckLoginFromMainLauncherActivityTest {

    private lateinit var app: Application
    private lateinit var sharedPrefs: SharedPreferences
    @Before
    fun setUp() {
        FirebaseApp.initializeApp(RuntimeEnvironment.application)
        app = (RuntimeEnvironment.application as Application)

        sharedPrefs = getRoboSharedPreferences()

        createMockForLocalDbManager(app)
        createMockForRemoteDbManager(app)
        createMockForSecureDataManager(app)

        mockIsSignedIn(app, sharedPrefs)
        createMockForDbManager(app)
        app.dbManager.initialiseDb()
    }

    @Test
    fun appNotSignedInFirebase_shouldRequestLoginActComeUp() {
        sharedPrefs.edit().putBoolean("IS_FIREBASE_TOKEN_VALID", false).commit()
        startCheckLoginAndCheckNextActivity(RequestLoginActivity::class.java)
    }

    @Test
    fun projectIdEmpty_shouldRequestLoginActComeUp() {
        getRoboSharedPreferences().edit().putString(LoginInfoManagerImpl.PROJECT_ID, "").commit()
        startCheckLoginAndCheckNextActivity(RequestLoginActivity::class.java)
    }

    @Test
    fun projectSecretEmpty_shouldRequestLoginActComeUp() {
        getRoboSharedPreferences().edit().putString(LoginInfoManagerImpl.ENCRYPTED_PROJECT_SECRET, "").commit()
        startCheckLoginAndCheckNextActivity(RequestLoginActivity::class.java)
    }

    @Test
    fun userIdEmpty_shouldRequestLoginActComeUp() {
        getRoboSharedPreferences().edit().putString(LoginInfoManagerImpl.USER_ID, "").commit()
        startCheckLoginAndCheckNextActivity(RequestLoginActivity::class.java)
    }

    @Test
    fun userIsLogged_shouldDashboardActComeUp() {
        startCheckLoginAndCheckNextActivity(DashboardActivity::class.java)
    }

    private fun startCheckLoginAndCheckNextActivity(clazzNextActivity: Class<out Activity>) {
        val controller = createRoboCheckLoginMainLauncherAppActivity()
        val activity = controller.get()
        controller.resume().visible()
        assertActivityStarted(clazzNextActivity, activity)
    }
}
