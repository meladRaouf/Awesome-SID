package com.simprints.infra.config.store.models

/**
 * Thin wrapper around custom project configuration to keep all the experimental
 * feature definitions in a single place and make calls explicit and type-safe.
 */
data class ExperimentalProjectConfiguration(
    private val customConfig: Map<String, Any>?,
) {
    val idPoolValidationEnabled: Boolean
        get() = customConfig
            ?.get(ENABLE_ID_POOL_VALIDATION)
            ?.let { it as? Boolean }
            .let { it == true }

    val singleQualityFallbackRequired: Boolean
        get() = customConfig
            ?.get(SINGLE_GOOD_QUALITY_FALLBACK_REQUIRED)
            ?.let { it as? Boolean }
            .let { it == true }

    val faceAutoCaptureConfig: Map<String, *>
        get() = customConfig
            ?.get(FACE_AUTO_CAPTURE)
            ?.let { it as? Map<*, *> }
            ?.let { map -> map.mapKeys { it.key.toString() } }
            ?: emptyMap<String, String>()

    companion object {
        internal const val ENABLE_ID_POOL_VALIDATION = "validateIdentificationPool"
        internal const val SINGLE_GOOD_QUALITY_FALLBACK_REQUIRED = "singleQualityFallbackRequired"
        internal const val FACE_AUTO_CAPTURE = "faceAutoCapture"
    }
}
