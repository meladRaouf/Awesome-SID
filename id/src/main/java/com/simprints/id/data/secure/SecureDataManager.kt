package com.simprints.id.data.secure

interface SecureDataManager {

    var apiKey: String

    fun getApiKeyOrDefault(): String

}
