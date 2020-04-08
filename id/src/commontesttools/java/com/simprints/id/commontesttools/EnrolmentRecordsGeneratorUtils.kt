package com.simprints.id.commontesttools

import com.simprints.id.data.db.person.domain.personevents.*
import java.util.*

object EnrolmentRecordsGeneratorUtils {
    fun getRandomEnrolmentEvents(nPeople: Int,
                                 projectId: String,
                                 userId: String,
                                 moduleId: String) =
        mutableListOf<Event>().also { fakeRecords ->
            repeat(nPeople) {
                fakeRecords.add(
                    Event(
                        UUID.randomUUID().toString(),
                        listOf("labels"),
                        buildFakeEnrolmentRecordCreation(projectId, userId, moduleId)
                    )
                )
            }
        }

    private fun buildFakeEnrolmentRecordCreation(projectId: String,
                                                 userId: String,
                                                 moduleId: String) = EnrolmentRecordCreation(
        subjectId = UUID.randomUUID().toString(),
        projectId = projectId,
        moduleId = moduleId,
        attendantId = userId,
        biometricReferences = buildFakeBiometricReferences()
    )

    private fun buildFakeBiometricReferences(): List<BiometricReference> {
        val fingerprint = FingerprintGeneratorUtils.generateRandomFingerprint()

        return listOf(
            FaceReference("metadata", arrayOf(FaceTemplate("face_template"))),
            FingerprintReference("metadata", arrayOf(
                FingerprintTemplate(fingerprint.templateQualityScore,
                    fingerprint.template.toString(), FingerIdentifier.LEFT_3RD_FINGER)
            ))
        )
    }
}
