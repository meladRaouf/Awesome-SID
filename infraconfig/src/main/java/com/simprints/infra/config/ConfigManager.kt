package com.simprints.infra.config

import com.simprints.infra.config.domain.models.Project

interface ConfigManager {
    suspend fun refreshProject(projectId: String): Project
    suspend fun getProject(projectId: String): Project
}
