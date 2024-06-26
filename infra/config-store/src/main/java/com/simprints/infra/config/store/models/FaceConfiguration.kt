package com.simprints.infra.config.store.models

data class FaceConfiguration(
    val nbOfImagesToCapture: Int,
    val qualityThreshold: Int,
    val imageSavingStrategy: ImageSavingStrategy,
    val decisionPolicy: DecisionPolicy,
) {

    enum class ImageSavingStrategy {
        NEVER,
        ONLY_USED_IN_REFERENCE,
        ONLY_GOOD_SCAN;

        fun shouldSaveImage() = this != NEVER
    }
}
