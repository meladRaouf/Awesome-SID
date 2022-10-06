package com.simprints.infra.config.domain

import com.simprints.infra.config.domain.models.DeviceConfiguration
import com.simprints.infra.config.domain.models.Project
import com.simprints.infra.config.domain.models.ProjectConfiguration

interface ConfigService {

    suspend fun refreshProject(projectId: String): Project
    suspend fun getProject(projectId: String): Project
    suspend fun getConfiguration(): ProjectConfiguration
    suspend fun refreshConfiguration(projectId: String): ProjectConfiguration
    suspend fun getDeviceConfiguration(): DeviceConfiguration
    suspend fun updateDeviceConfiguration(update: suspend (t: DeviceConfiguration) -> DeviceConfiguration)
    suspend fun clearData()
}
