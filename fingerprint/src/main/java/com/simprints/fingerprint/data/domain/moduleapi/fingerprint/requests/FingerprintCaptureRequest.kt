package com.simprints.fingerprint.data.domain.moduleapi.fingerprint.requests

import com.simprints.fingerprint.data.domain.fingerprint.FingerIdentifier
import kotlinx.android.parcel.Parcelize

@Parcelize
data class FingerprintCaptureRequest(val fingerprintsToCapture: List<FingerIdentifier>) : FingerprintRequest
