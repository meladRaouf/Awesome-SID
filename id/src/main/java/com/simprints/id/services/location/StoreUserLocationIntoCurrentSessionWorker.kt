package com.simprints.id.services.location

import android.content.Context
import androidx.work.WorkerParameters
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.Priority
import com.simprints.core.tools.coroutines.DispatcherProvider
import com.simprints.eventsystem.event.EventRepository
import com.simprints.eventsystem.event.domain.models.session.Location
import com.simprints.id.services.sync.events.common.SimCoroutineWorker
import com.simprints.id.tools.LocationManager
import com.simprints.infra.logging.Simber
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Worker that collects user's last known location and save it into current session
 */
class StoreUserLocationIntoCurrentSessionWorker(context: Context, params: WorkerParameters) :
    SimCoroutineWorker(context, params) {


    override val tag: String = StoreUserLocationIntoCurrentSessionWorker::class.java.simpleName

    @Inject
    lateinit var eventRepository: EventRepository

    @Inject
    lateinit var locationManager: LocationManager

    @Inject
    lateinit var dispatcherProvider: DispatcherProvider

    override suspend fun doWork(): Result {
        getComponent<StoreUserLocationIntoCurrentSessionWorker> {
            it.inject(this@StoreUserLocationIntoCurrentSessionWorker)
        }
        return withContext(dispatcherProvider.main()) {
            try {
                val locationsFlow = createLocationFlow()
                locationsFlow.filterNotNull().collect { location ->
                    runCatching {
                        saveUserLocation(location)
                    }
                }
            } catch (t: Throwable) {
                Simber.e(t)
                fail(t)
            }
            success()
        }
    }

    private fun createLocationFlow(): Flow<android.location.Location?> {
        val locationRequest = LocationRequest.create().apply {
            priority = Priority.PRIORITY_HIGH_ACCURACY
        }
        return locationManager.requestLocation(locationRequest).take(1)
    }

    private suspend fun saveUserLocation(lastLocation: android.location.Location) {
        val currentSession = eventRepository.getCurrentCaptureSessionEvent()
        currentSession.payload.location =
            Location(lastLocation.latitude, lastLocation.longitude)
        eventRepository.addOrUpdateEvent(currentSession)
        Simber.d("Saving user's location into the current session")
    }
}
