package com.simprints.face.capture

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.fragment.app.Fragment
import androidx.navigation.findNavController
import com.simprints.core.livedata.LiveDataEventObserver
import com.simprints.core.livedata.LiveDataEventWithContentObserver
import com.simprints.core.tools.viewbinding.viewBinding
import com.simprints.core.tools.whenNonNull
import com.simprints.core.tools.whenNull
import com.simprints.face.R
import com.simprints.face.base.FaceActivity
import com.simprints.face.controllers.core.events.model.RefusalAnswer.Companion.fromExitFormOption
import com.simprints.face.data.moduleapi.face.requests.FaceCaptureRequest
import com.simprints.face.data.moduleapi.face.responses.FaceExitFormResponse
import com.simprints.face.databinding.ActivityFaceCaptureBinding
import com.simprints.face.exceptions.InvalidFaceRequestException
import com.simprints.feature.exitform.ExitFormContract
import com.simprints.feature.exitform.config.ExitFormOption
import com.simprints.feature.exitform.exitFormConfiguration
import com.simprints.feature.exitform.toArgs
import com.simprints.moduleapi.face.requests.IFaceRequest
import com.simprints.moduleapi.face.responses.IFaceResponse
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class FaceCaptureActivity : FaceActivity() {
    private val vm: FaceCaptureViewModel by viewModels()

    private val binding by viewBinding(ActivityFaceCaptureBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        bindViewModel()

        val faceRequest: FaceCaptureRequest =
            this.intent.extras?.getParcelable(IFaceRequest.BUNDLE_KEY)
                ?: throw InvalidFaceRequestException("No IFaceRequest found for FaceCaptureActivity")

        binding.captureHostFragment.getFragment<Fragment>().childFragmentManager
            .setFragmentResultListener(ExitFormContract.EXIT_FORM_REQUEST, this) { _, data ->
                val formSubmitted = ExitFormContract.isFormSubmitted(data)
                val option = ExitFormContract.getFormOption(data)
                val reason = ExitFormContract.getFormReason(data).orEmpty()

                if (formSubmitted && option != null) {
                    setResultAndFinish(option, reason)
                } else {
                    findNavController(R.id.capture_host_fragment).navigate(R.id.action_global_liveFeedback)
                }
            }

        vm.setupCapture(faceRequest)
    }

    private fun setResultAndFinish(option: ExitFormOption, reason: String) {
        val intent = Intent().apply {
            putExtra(IFaceResponse.BUNDLE_KEY, FaceExitFormResponse(fromExitFormOption(option), reason))
        }
        setResult(RESULT_OK, intent)
        finish()
    }

    private fun bindViewModel() {
        vm.finishFlowEvent.observe(this, LiveDataEventWithContentObserver {
            val intent = Intent().apply { putExtra(IFaceResponse.BUNDLE_KEY, it) }
            setResult(Activity.RESULT_OK, intent)
            finish()
        })

        vm.recaptureEvent.observe(this, LiveDataEventObserver {
            findNavController(R.id.capture_host_fragment).navigate(R.id.action_confirmationFragment_to_liveFeedbackFragment)
        })

        vm.exitFormEvent.observe(this, LiveDataEventObserver {
            findNavController(R.id.capture_host_fragment).navigate(
                R.id.action_global_refusalFragment,
                exitFormConfiguration {
                    titleRes = R.string.why_did_you_skip_face_capture
                    backButtonRes = R.string.exit_form_return_to_face_capture
                }.toArgs()
            )
        })

        vm.unexpectedErrorEvent.observe(this, LiveDataEventObserver {
            setResult(Activity.RESULT_CANCELED)
            finish()
        })
    }

    override fun onBackPressed() {
        BackButtonContext.fromFragmentId(findNavController(R.id.capture_host_fragment).currentDestination?.id)
            .whenNonNull { vm.handleBackButton(this) }
            .whenNull { super.onBackPressed() }
    }

    enum class BackButtonContext {
        CAPTURE;

        companion object {
            fun fromFragmentId(fragmentId: Int?): BackButtonContext? = when (fragmentId) {
                R.id.preparationFragment, R.id.liveFeedbackFragment -> CAPTURE
                else -> null
            }
        }
    }

    companion object {
        fun getStartingIntent(context: Context, faceCaptureRequest: FaceCaptureRequest): Intent =
            Intent(context, FaceCaptureActivity::class.java).apply {
                putExtra(IFaceRequest.BUNDLE_KEY, faceCaptureRequest)
            }
    }

}
