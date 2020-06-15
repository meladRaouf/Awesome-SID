package com.simprints.id.secure.securitystate.repository

import com.simprints.id.secure.models.SecurityState
import com.simprints.id.secure.securitystate.remote.SecurityStateRemoteDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

@ExperimentalCoroutinesApi
class SecurityStateRepositoryImpl(
    private val remoteDataSource: SecurityStateRemoteDataSource
) : SecurityStateRepository {

    override var securityStatusChannel: Channel<SecurityState.Status> = Channel(Channel.CONFLATED)

    init {
        CoroutineScope(Dispatchers.Main).launch {
            securityStatusChannel.update(SecurityState.Status.RUNNING)
        }
    }

    override suspend fun getSecurityState(): SecurityState {
        return remoteDataSource.getSecurityState().also {
            securityStatusChannel.update(it.status)
        }
    }

    private suspend fun Channel<SecurityState.Status>.update(status: SecurityState.Status) {
        if (!isClosedForSend)
            send(status)
    }

}
