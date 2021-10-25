package com.simprints.id.network

import com.simprints.core.network.SimApiClient
import com.simprints.core.network.SimApiClientFactory
import com.simprints.core.network.SimRemoteInterface
import com.simprints.core.tools.coroutines.DispatcherProvider
import com.simprints.core.tools.json.JsonHelper
import com.simprints.id.data.db.common.RemoteDbManager
import okhttp3.Interceptor
import kotlin.reflect.KClass

class SimApiClientFactoryImpl(
    val baseUrlProvider: BaseUrlProvider,
    val deviceId: String,
    private val versionName: String,
    private val remoteDbManager: RemoteDbManager,
    private val jsonHelper: JsonHelper,
    private val dispatcher: DispatcherProvider,
    private val interceptor: Interceptor,
    private val okHttpClientBuilder: DefaultOkHttpClientBuilder = DefaultOkHttpClientBuilder()
): SimApiClientFactory {

    // Not using `inline fun <reified T : SimRemoteInterface>` because it's not possible to
    // create an interface for that or mock it. SimApiClientFactory is injected everywhere, so it's important
    // that we are able to mock it.
    override suspend fun <T : SimRemoteInterface> buildClient(remoteInterface: KClass<T>): SimApiClient<T> {
        return SimApiClientImpl(
            remoteInterface,
            baseUrlProvider.getApiBaseUrl(),
            deviceId,
            versionName,
            remoteDbManager.getCurrentToken(),
            jsonHelper,
            dispatcher,
            interceptor,
            okHttpClientBuilder
        )
    }

    override fun <T : SimRemoteInterface> buildUnauthenticatedClient(remoteInterface: KClass<T>): SimApiClient<T> {
        return SimApiClientImpl(
            remoteInterface,
            baseUrlProvider.getApiBaseUrl(),
            deviceId,
            versionName,
            null,
            jsonHelper,
            dispatcher,
            interceptor,
            okHttpClientBuilder
        )
    }
}
