package com.simprints.id.data.analytics

import com.simprints.id.domain.ALERT_TYPE
import com.simprints.id.domain.request.IdRequest
import com.simprints.id.exceptions.safe.SimprintsException
import com.simprints.id.exceptions.unsafe.SimprintsError
import com.simprints.id.session.Session
import io.reactivex.Single


interface AnalyticsManager {

    val analyticsId: Single<String>

    fun logThrowable(throwable: Throwable)
    fun logError(error: SimprintsError)
    fun logSafeException(exception: SimprintsException)

    fun logCallout(idRequest: IdRequest)
    fun logAlert(alertType: ALERT_TYPE)
    fun logUserProperties()
    fun logScannerProperties()
    fun logGuidSelectionService(projectId: String, sessionId: String, selectedGuid: String, callbackSent: Boolean)
    fun logConnectionStateChange(connected: Boolean)
    fun logAuthStateChange(authenticated: Boolean)
    fun logSession(session: Session)
}
