package com.simprints.id.data.prefs.events

import java.util.*

interface EventsPreferencesManager {

    var lastScannerUsed: String

    var lastIdentificationDate: Date?
    var lastEnrolDate: Date?
    var lastVerificationDate: Date?
}
