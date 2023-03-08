package com.simprints.infra.events.exceptions

import com.simprints.core.exceptions.UnexpectedException


internal class MissingArgumentForDownSyncScopeException(
    message: String = "MissingArgumentForDownSyncScopeException"
) : UnexpectedException(message)
