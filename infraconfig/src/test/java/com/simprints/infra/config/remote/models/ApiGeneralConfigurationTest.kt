package com.simprints.infra.config.remote.models

import com.google.common.truth.Truth.assertThat
import com.simprints.infra.config.domain.GeneralConfiguration
import com.simprints.infra.config.testtools.apiGeneralConfiguration
import com.simprints.infra.config.testtools.generalConfiguration
import org.junit.Test

class ApiGeneralConfigurationTest {

    @Test
    fun `should map correctly the model`() {
        assertThat(apiGeneralConfiguration.toDomain()).isEqualTo(generalConfiguration)
    }

    @Test
    fun `should map correctly the Modality enums`() {
        val mapping = mapOf(
            ApiGeneralConfiguration.Modality.FACE to GeneralConfiguration.Modality.FACE,
            ApiGeneralConfiguration.Modality.FINGERPRINT to GeneralConfiguration.Modality.FINGERPRINT,
        )

        mapping.forEach {
            assertThat(it.key.toDomain()).isEqualTo(it.value)
        }
    }
}
