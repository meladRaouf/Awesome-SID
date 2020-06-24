package com.simprints.id.activities.fingerprintexitform

import androidx.lifecycle.ViewModel
import com.simprints.id.data.db.event.SessionRepository
import com.simprints.id.data.db.event.domain.events.RefusalEvent
import com.simprints.id.data.exitform.FingerprintExitFormReason
import com.simprints.id.data.exitform.toRefusalEventAnswer

class FingerprintExitFormViewModel(private val sessionRepository: SessionRepository) : ViewModel() {

    fun addExitFormEvent(startTime: Long, endTime: Long, otherText: String,
                         fingerprintExitFormReason: FingerprintExitFormReason) {
        sessionRepository.addEventToCurrentSessionInBackground(RefusalEvent(startTime, endTime,
            fingerprintExitFormReason.toRefusalEventAnswer(), otherText))
    }
}
