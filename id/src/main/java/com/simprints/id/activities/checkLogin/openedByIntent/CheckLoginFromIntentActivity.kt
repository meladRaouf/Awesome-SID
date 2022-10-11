package com.simprints.id.activities.checkLogin.openedByIntent

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View.VISIBLE
import androidx.lifecycle.lifecycleScope
import com.simprints.core.sharedpreferences.PreferencesManager
import com.simprints.core.tools.activity.BaseSplitActivity
import com.simprints.core.tools.extentions.removeAnimationsToNextActivity
import com.simprints.core.tools.viewbinding.viewBinding
import com.simprints.id.Application
import com.simprints.id.R
import com.simprints.id.activities.alert.AlertActivityHelper.extractPotentialAlertScreenResponse
import com.simprints.id.activities.alert.AlertActivityHelper.launchAlert
import com.simprints.id.activities.login.LoginActivity
import com.simprints.id.activities.login.request.LoginActivityRequest
import com.simprints.id.activities.login.response.LoginActivityResponse
import com.simprints.id.activities.orchestrator.OrchestratorActivity
import com.simprints.id.databinding.CheckLoginFromIntentScreenBinding
import com.simprints.id.domain.alert.AlertType
import com.simprints.id.domain.moduleapi.app.requests.AppRequest
import com.simprints.id.domain.moduleapi.app.responses.AppErrorResponse
import com.simprints.clientapi.InternalConstants.RequestIntents.Companion.LOGIN_ACTIVITY_REQUEST
import com.simprints.id.tools.extensions.deviceId
import com.simprints.id.tools.extensions.parseAppRequest
import com.simprints.moduleapi.app.responses.IAppErrorResponse
import com.simprints.moduleapi.app.responses.IAppResponse
import javax.inject.Inject

// App launched when user open SimprintsID using a client app (by intent)
open class CheckLoginFromIntentActivity : BaseSplitActivity(), CheckLoginFromIntentContract.View {

    @Inject lateinit var preferencesManager: PreferencesManager

    private val binding by viewBinding(CheckLoginFromIntentScreenBinding::inflate)

    override lateinit var viewPresenter: CheckLoginFromIntentContract.Presenter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        val component = (application as Application).component
        component.inject(this)
        title = getString(R.string.title_activity_front)
        viewPresenter = CheckLoginFromIntentPresenter(this, deviceId, component)

        lifecycleScope.launchWhenCreated {
            viewPresenter.setup()
            viewPresenter.start()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        val potentialAlertScreenResponse = extractPotentialAlertScreenResponse(data)
        val appErrorResponseForLoginScreen = extractAppErrorResponseForLoginScreen(data)

        when {
            potentialAlertScreenResponse != null -> viewPresenter.onAlertScreenReturn(potentialAlertScreenResponse)
            appErrorResponseForLoginScreen != null -> viewPresenter.onLoginScreenErrorReturn(appErrorResponseForLoginScreen)
            else -> lifecycleScope.launchWhenCreated {
                viewPresenter.checkSignedInStateIfPossible()
            }
        }
    }

    private fun extractAppErrorResponseForLoginScreen(data: Intent?): AppErrorResponse? =
        data?.getParcelableExtra(LoginActivityResponse.BUNDLE_KEY)


    override fun setResultErrorAndFinish(appResponse: IAppErrorResponse) {
        setResult(Activity.RESULT_OK, Intent().apply {
            putExtra(IAppResponse.BUNDLE_KEY, appResponse)
        })
        finish()
    }

    override fun parseRequest() =
        intent.parseAppRequest()

    override fun getCheckCallingApp() = getCallingPackageName()

    override fun showConfirmationText() {
        binding.confirmationSent.visibility = VISIBLE
        binding.redirectingBack.visibility = VISIBLE
    }
    open fun getCallingPackageName(): String {
        return callingPackage ?: ""
    }

    override fun openAlertActivityForError(alertType: AlertType) {
        launchAlert(this, alertType)
    }

    override fun openLoginActivity(appRequest: AppRequest) {
        val loginIntent = Intent(this, LoginActivity::class.java)
        loginIntent.putExtra(LoginActivityRequest.BUNDLE_KEY, LoginActivityRequest(appRequest.projectId, appRequest.userId))
        startActivityForResult(loginIntent, LOGIN_ACTIVITY_REQUEST)
    }

    override fun openOrchestratorActivity(appRequest: AppRequest) {
        val intent = Intent(this, OrchestratorActivity::class.java).apply {
            putExtra(AppRequest.BUNDLE_KEY, appRequest)
            addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT)
        }
        startActivity(intent)
        this.removeAnimationsToNextActivity()
        finish()
    }

    override fun finishCheckLoginFromIntentActivity() {
        finish()
    }
}
