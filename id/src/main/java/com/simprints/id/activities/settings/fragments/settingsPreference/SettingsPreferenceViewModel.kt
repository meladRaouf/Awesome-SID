package com.simprints.id.activities.settings.fragments.settingsPreference

import androidx.lifecycle.ViewModel
import com.simprints.infra.logging.LoggingConstants.CrashReportTag
import com.simprints.infra.logging.Simber

class SettingsPreferenceViewModel : ViewModel() {

    fun logMessageForCrashReport(message: String) {
        Simber.tag(CrashReportTag.SETTINGS.name).i(message)
    }
}
