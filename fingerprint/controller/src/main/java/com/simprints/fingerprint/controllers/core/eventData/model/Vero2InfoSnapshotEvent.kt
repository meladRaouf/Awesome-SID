package com.simprints.fingerprint.controllers.core.eventData.model

import androidx.annotation.Keep
import com.simprints.fingerprint.infra.scanner.domain.versions.ScannerVersion
import com.simprints.fingerprint.infra.scanner.domain.BatteryInfo as BatteryInfoDomain
import com.simprints.infra.events.event.domain.models.Vero2InfoSnapshotEvent as Vero2InfoSnapshotEventCore
import com.simprints.infra.events.event.domain.models.Vero2InfoSnapshotEvent.BatteryInfo as BatteryInfoCore
import com.simprints.infra.events.event.domain.models.Vero2InfoSnapshotEvent.Vero2Version as Vero2VersionCore

/**
 * This class represents a snapshot of the current state of the vero 2 scanner, capturing basic info.
 *
 * @property version  the current unified version info of the vero 2 scanner
 * @property battery  the current battery info of the vero 2 scanner
 */
@Keep
class Vero2InfoSnapshotEvent(startTime: Long,
                             val version: Vero2Version,
                             val battery: BatteryInfo) : Event(EventType.VERO_2_INFO_SNAPSHOT, startTime) {

    @Keep
    class Vero2Version(
        val cypressApp: String,
        val stmApp: String,
        val un20App: String,
        val hardwareVersion: String
    ) {
        companion object {
            fun get(scannerVersion: ScannerVersion) =
                with(scannerVersion) {
                    Vero2Version(
                        cypressApp = firmware.cypress,
                        stmApp = firmware.stm,
                        un20App = firmware.un20,
                        hardwareVersion = hardwareVersion
                    )
                }
        }
    }

    @Keep
    class BatteryInfo(
        val charge: Int,
        val voltage: Int,
        val current: Int,
        val temperature: Int
    ) {
        companion object {
            fun get(batteryInfo: BatteryInfoDomain) =
                with(batteryInfo) {
                    BatteryInfo(charge, voltage, current, temperature)
                }
        }
    }
}

fun Vero2InfoSnapshotEvent.fromDomainToCore(): Vero2InfoSnapshotEventCore =
    Vero2InfoSnapshotEventCore(startTime, version.fromDomainToCore(), battery.fromDomainToCore())

fun Vero2InfoSnapshotEvent.Vero2Version.fromDomainToCore(): Vero2VersionCore.Vero2NewApiVersion =
    Vero2VersionCore.Vero2NewApiVersion(hardwareVersion, cypressApp, stmApp, un20App)

fun Vero2InfoSnapshotEvent.BatteryInfo.fromDomainToCore(): BatteryInfoCore =
    BatteryInfoCore(charge, voltage, current, temperature)
