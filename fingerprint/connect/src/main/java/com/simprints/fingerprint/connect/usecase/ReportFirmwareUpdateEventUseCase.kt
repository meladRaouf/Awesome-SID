package com.simprints.fingerprint.connect.usecase

import com.simprints.core.ExternalScope
import com.simprints.core.tools.time.TimeHelper
import com.simprints.fingerprint.infra.scanner.domain.ota.AvailableOta
import com.simprints.infra.events.EventRepository
import com.simprints.infra.events.event.domain.models.ScannerFirmwareUpdateEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

class ReportFirmwareUpdateEventUseCase @Inject constructor(
    private val timeHelper: TimeHelper,
    private val eventRepository: EventRepository,
    @ExternalScope private val externalScope: CoroutineScope,
) {

    operator fun invoke(
        startTime: Long,
        availableOta: AvailableOta,
        targetVersions: String,
        e: Throwable? = null,
    ) = externalScope.launch {
        val chipName = when (availableOta) {
            AvailableOta.CYPRESS -> "cypress"
            AvailableOta.STM -> "stm"
            AvailableOta.UN20 -> "un20"
        }
        val failureReason = e?.let { "${it::class.java.simpleName} : ${it.message}" }

        eventRepository.addOrUpdateEvent(ScannerFirmwareUpdateEvent(
            startTime,
            timeHelper.now(),
            chipName,
            targetVersions,
            failureReason,
        ))
    }
}
