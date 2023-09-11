package com.simprints.infra.enrolment.records.domain.models

import android.os.Parcelable
import com.simprints.core.domain.face.FaceSample
import com.simprints.core.domain.fingerprint.FingerprintSample
import com.simprints.core.domain.tokenization.TokenizedString
import kotlinx.parcelize.Parcelize
import java.util.*

@Parcelize
data class Subject(
    val subjectId: String,
    val projectId: String,
    val attendantId: TokenizedString,
    val moduleId: TokenizedString,
    val createdAt: Date? = null,
    val updatedAt: Date? = null,
    var fingerprintSamples: List<FingerprintSample> = emptyList(),
    var faceSamples: List<FaceSample> = emptyList(),

    @Deprecated("See SubjectToEventDbMigrationManagerImpl doc")
    val toSync: Boolean = false
) : Parcelable
