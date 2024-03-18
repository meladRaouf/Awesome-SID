package com.simprints.face.capture.screens.preparation

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.simprints.core.tools.time.TimeHelper
import com.simprints.core.tools.time.Timestamp
import com.simprints.face.capture.R
import com.simprints.face.capture.databinding.FragmentPreparationBinding
import com.simprints.infra.uibase.viewbinding.viewBinding
import com.simprints.face.capture.screens.FaceCaptureViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * This class represents the screen the user is presented to prepare them on how to capture the face
 */
@AndroidEntryPoint
internal class PreparationFragment : Fragment(R.layout.fragment_preparation) {

    private val binding by viewBinding(FragmentPreparationBinding::bind)

    private val mainVm: FaceCaptureViewModel by activityViewModels()

    @Inject
    lateinit var faceTimeHelper: TimeHelper
    private var startTime: Timestamp = Timestamp(0)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        startTime = faceTimeHelper.now()

        binding.detectionOnboardingFrame.setOnClickListener {
            mainVm.addOnboardingComplete(startTime)
            findNavController().navigate(R.id.action_facePreparationFragment_to_faceLiveFeedbackFragment)
        }
    }
}
