package com.simprints.infra.config.local.migrations.models

import androidx.annotation.Keep
import com.fasterxml.jackson.annotation.JsonProperty
import com.simprints.core.tools.json.JsonHelper
import com.simprints.infra.config.domain.models.*
import org.json.JSONObject


@Keep
internal data class OldProjectConfig(
    @JsonProperty("CaptureFingerprintStrategy") private val captureFingerprintStrategy: String?,
    @JsonProperty("ConsentRequired") private val consentRequired: String,
    @JsonProperty("SaveFingerprintImagesStrategy") private val saveFingerprintImagesStrategy: String?,
    @JsonProperty("Vero2FirmwareVersions") private val vero2FirmwareVersions: String?,
    @JsonProperty("FingerprintQualityThreshold") private val fingerprintQualityThreshold: String?,
    @JsonProperty("FingerprintsToCollect") private val fingerprintsToCollect: String?,
    @JsonProperty("ConsentParentalExists") private val consentParentalExists: String,
    @JsonProperty("MaxNbOfModules") private val maxNbOfModules: String,
    @JsonProperty("FaceQualityThreshold") private val faceQualityThreshold: String?,
    @JsonProperty("LogoExists") private val logoExists: String,
    @JsonProperty("EnrolmentPlus") private val enrolmentPlus: String,
    @JsonProperty("FingerComparisonStrategyForVerification") private val fingerComparisonStrategyForVerification: String?,
    @JsonProperty("FingerStatus") private val fingerStatus: String?,
    @JsonProperty("ScannerGenerations") private val scannerGenerations: String?,
    @JsonProperty("FaceNbOfFramesCaptured") private val faceNbOfFramesCaptured: String?,
    @JsonProperty("ProjectSpecificMode") private val projectSpecificMode: String,
    @JsonProperty("OrganizationName") private val organizationName: String,
    @JsonProperty("SelectedLanguage") private val selectedLanguage: String,
    @JsonProperty("ProjectLanguages") private val projectLanguages: String,
    @JsonProperty("FingerprintLiveFeedbackOn") private val fingerprintLiveFeedbackOn: String?,
    @JsonProperty("FaceConfidenceThresholds") private val faceConfidenceThresholds: String?,
    @JsonProperty("MatchGroup") private val matchGroup: String,
    @JsonProperty("SyncDestination") private val syncDestination: String?,
    @JsonProperty("SimprintsSync") private val simprintsSync: String?,
    @JsonProperty("LocationRequired") private val locationRequired: String,
    @JsonProperty("FingerImagesExist") private val fingerImagesExist: String?,
    @JsonProperty("ConsentParentalOptions") private val consentParentalOptions: String,
    @JsonProperty("SaveFaceImages") private val saveFaceImages: String?,
    @JsonProperty("ConsentGeneralOptions") private val consentGeneralOptions: String,
    @JsonProperty("DownSyncSetting") private val downSyncSetting: String,
    @JsonProperty("CoSync") private val coSync: String?,
    @JsonProperty("ModuleIdOptions") private val moduleIdOptions: String,
    @JsonProperty("FingerprintConfidenceThresholds") private val fingerprintConfidenceThresholds: String?,
    @JsonProperty("ProgramName") private val programName: String,
    @JsonProperty("SyncGroup") private val syncGroup: String,
    @JsonProperty("NbOfIdsInt") private val nbOfIdsInt: String,
    @JsonProperty("Modality") private val modality: String,
    @JsonProperty("Custom") private val custom: Any?
) {
    fun toDomain(projectId: String): ProjectConfiguration =
        ProjectConfiguration(
            projectId = projectId,
            general = generalConfiguration(),
            face = faceConfiguration(),
            fingerprint = fingerprintConfiguration(),
            consent = consentConfiguration(),
            identification = identificationConfiguration(),
            synchronization = synchronizationConfiguration(),
        )

    private fun generalConfiguration(): GeneralConfiguration =
        GeneralConfiguration(
            modalities = modality.split(",")
                .map { if (it == "FINGER") "FINGERPRINT" else it }
                .map { GeneralConfiguration.Modality.valueOf(it) },
            languageOptions = projectLanguages.split(","),
            defaultLanguage = selectedLanguage,
            collectLocation = locationRequired.toBoolean(),
            duplicateBiometricEnrolmentCheck = enrolmentPlus.toBoolean(),
            settingsPassword = SettingsPasswordConfig.NotSet,
        )

    private fun faceConfiguration(): FaceConfiguration? =
        if (faceQualityThreshold == null) null
        else
            FaceConfiguration(
                nbOfImagesToCapture = faceNbOfFramesCaptured?.toIntOrNull()
                    ?: DEFAULT_FACE_FRAMES_TO_CAPTURE,
                qualityThreshold = faceQualityThreshold.toInt(),
                imageSavingStrategy = if (saveFaceImages.toBoolean()) {
                    FaceConfiguration.ImageSavingStrategy.ONLY_GOOD_SCAN
                } else {
                    FaceConfiguration.ImageSavingStrategy.NEVER
                },
                decisionPolicy = faceConfidenceThresholds?.let { parseDecisionPolicy(it) }
                    ?: DecisionPolicy(0, 0, 0),
            )

    private fun fingerprintConfiguration(): FingerprintConfiguration? =
        if (fingerprintQualityThreshold == null) null
        else
            FingerprintConfiguration(
                fingersToCapture = fingerprintsToCollect?.split(",")
                    ?.map { Finger.valueOf(it) }
                    ?: listOf(Finger.LEFT_THUMB, Finger.LEFT_INDEX_FINGER),
                qualityThreshold = fingerprintQualityThreshold.toInt(),
                decisionPolicy = fingerprintConfidenceThresholds?.let { parseDecisionPolicy(it) }
                    ?: DecisionPolicy(0, 0, 700),
                allowedVeroGenerations = scannerGenerations?.split(",")
                    ?.map { FingerprintConfiguration.VeroGeneration.valueOf(it) }
                    ?: listOf(FingerprintConfiguration.VeroGeneration.VERO_1),
                comparisonStrategyForVerification = fingerComparisonStrategyForVerification
                    ?.let { FingerprintConfiguration.FingerComparisonStrategy.valueOf(it) }
                    ?: FingerprintConfiguration.FingerComparisonStrategy.SAME_FINGER,
                displayHandIcons = fingerImagesExist.toBoolean(),
                vero2 = vero2Configuration(),
            )

    private fun vero2Configuration(): Vero2Configuration? =
        if (captureFingerprintStrategy == null) null
        else
            Vero2Configuration(
                captureStrategy = Vero2Configuration.CaptureStrategy.valueOf(
                    captureFingerprintStrategy
                ),
                displayLiveFeedback = fingerprintLiveFeedbackOn.toBoolean(),
                imageSavingStrategy = when (saveFingerprintImagesStrategy) {
                    "NEVER" -> Vero2Configuration.ImageSavingStrategy.NEVER
                    "WSQ_15" -> Vero2Configuration.ImageSavingStrategy.ONLY_GOOD_SCAN
                    "WSQ_15_EAGER" -> Vero2Configuration.ImageSavingStrategy.EAGER
                    else -> Vero2Configuration.ImageSavingStrategy.NEVER
                },
                firmwareVersions = if (vero2FirmwareVersions.isNullOrEmpty()) {
                    emptyMap()
                } else {
                    // Construct a JavaType instance for Map<String, Vero2FirmwareVersions>
                    val type = JsonHelper.jackson.typeFactory.constructMapType(
                        Map::class.java,
                        String::class.java,
                        Vero2Configuration.Vero2FirmwareVersions::class.java
                    )
                    JsonHelper.fromJson(vero2FirmwareVersions, type)
                },
            )

    private fun consentConfiguration(): ConsentConfiguration =
        ConsentConfiguration(
            programName = programName,
            organizationName = organizationName,
            collectConsent = consentRequired.toBoolean(),
            displaySimprintsLogo = logoExists.toBoolean(),
            allowParentalConsent = consentParentalExists.toBoolean(),
            generalPrompt = JsonHelper.fromJson<GeneralConsentOptions>(consentGeneralOptions)
                .toDomain(),
            parentalPrompt = JsonHelper.fromJson<ParentalConsentOptions>(consentParentalOptions)
                .toDomain(),
        )

    private fun identificationConfiguration(): IdentificationConfiguration =
        IdentificationConfiguration(
            maxNbOfReturnedCandidates = nbOfIdsInt.toInt(),
            poolType = IdentificationConfiguration.PoolType.valueOf(
                if (matchGroup == "GLOBAL") "PROJECT" else matchGroup
            ),
        )

    private fun synchronizationConfiguration(): SynchronizationConfiguration =
        SynchronizationConfiguration(
            frequency = when (downSyncSetting) {
                "OFF" -> SynchronizationConfiguration.Frequency.ONLY_PERIODICALLY_UP_SYNC
                "ON" -> SynchronizationConfiguration.Frequency.PERIODICALLY
                "EXTRA" -> SynchronizationConfiguration.Frequency.PERIODICALLY_AND_ON_SESSION_START
                else -> SynchronizationConfiguration.Frequency.PERIODICALLY
            },
            up = UpSynchronizationConfiguration(
                simprints = UpSynchronizationConfiguration.SimprintsUpSynchronizationConfiguration(
                    kind = if (simprintsSync == null) {
                        if (syncDestination?.contains("SIMPRINTS") == true) {
                            UpSynchronizationConfiguration.UpSynchronizationKind.ALL
                        } else {
                            UpSynchronizationConfiguration.UpSynchronizationKind.NONE
                        }
                    } else {
                        UpSynchronizationConfiguration.UpSynchronizationKind.valueOf(simprintsSync)
                    }
                ),
                coSync = UpSynchronizationConfiguration.CoSyncUpSynchronizationConfiguration(
                    kind = if (coSync == null) {
                        if (syncDestination?.contains("COMMCARE") == true) {
                            UpSynchronizationConfiguration.UpSynchronizationKind.ALL
                        } else {
                            UpSynchronizationConfiguration.UpSynchronizationKind.NONE
                        }
                    } else {
                        UpSynchronizationConfiguration.UpSynchronizationKind.valueOf(coSync)
                    }
                )
            ),
            down = DownSynchronizationConfiguration(
                partitionType = DownSynchronizationConfiguration.PartitionType.valueOf(
                    if (syncGroup == "GLOBAL") "PROJECT" else syncGroup
                ),
                maxNbOfModules = maxNbOfModules.toInt(),
                moduleOptions = moduleIdOptions.split("|")
            ),
        )

    private fun parseDecisionPolicy(decisionPolicy: String): DecisionPolicy =
        with(JSONObject(decisionPolicy)) {
            DecisionPolicy(
                low = getString("LOW").toInt(),
                medium = getString("MEDIUM").toInt(),
                high = getString("HIGH").toInt(),
            )
        }

    companion object {
        private const val DEFAULT_FACE_FRAMES_TO_CAPTURE = 2
    }
}

