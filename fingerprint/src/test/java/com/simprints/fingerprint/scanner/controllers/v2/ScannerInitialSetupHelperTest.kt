package com.simprints.fingerprint.scanner.controllers.v2

import com.google.common.truth.Truth.assertThat
import com.simprints.fingerprint.scanner.adapters.v2.toScannerVersion
import com.simprints.fingerprint.scanner.data.local.FirmwareLocalDataSource
import com.simprints.fingerprint.scanner.domain.BatteryInfo
import com.simprints.fingerprint.scanner.domain.ota.AvailableOta
import com.simprints.fingerprint.scanner.domain.ota.DownloadableFirmwareVersion
import com.simprints.fingerprint.scanner.domain.versions.ScannerVersion
import com.simprints.fingerprint.scanner.exceptions.safe.OtaAvailableException
import com.simprints.fingerprint.tools.BatteryLevelChecker
import com.simprints.fingerprintscanner.v2.domain.main.message.un20.models.Un20AppVersion
import com.simprints.fingerprintscanner.v2.domain.main.message.vero.models.StmFirmwareVersion
import com.simprints.fingerprintscanner.v2.domain.root.models.CypressFirmwareVersion
import com.simprints.fingerprintscanner.v2.domain.root.models.ScannerInformation
import com.simprints.fingerprintscanner.v2.domain.root.models.UnifiedVersionInformation
import com.simprints.fingerprintscanner.v2.scanner.Scanner
import com.simprints.infra.config.ConfigManager
import com.simprints.infra.config.domain.models.Vero2Configuration
import com.simprints.testtools.common.syntax.assertThrows
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.reactivex.Completable
import io.reactivex.Single
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test

class ScannerInitialSetupHelperTest {

    private val scannerMock = mockk<Scanner>()
    private val connectionHelperMock = mockk<ConnectionHelper>()
    private val batteryLevelChecker = mockk<BatteryLevelChecker>()
    private val vero2Configuration = mockk<Vero2Configuration>()
    private val configManager = mockk<ConfigManager> {
        coEvery { getProjectConfiguration() } returns mockk {
            every { fingerprint } returns mockk {
                every { vero2 } returns vero2Configuration
            }
        }
    }
    private val firmwareLocalDataSource = mockk<FirmwareLocalDataSource>()
    private val scannerInitialSetupHelper = ScannerInitialSetupHelper(
        connectionHelperMock,
        batteryLevelChecker,
        configManager,
        firmwareLocalDataSource,
    )

    @Before
    fun setup() {
        every { firmwareLocalDataSource.getAvailableScannerFirmwareVersions() } returns LOCAL_SCANNER_VERSION
        every { scannerMock.enterMainMode() } returns Completable.complete()
        coEvery {
            connectionHelperMock.reconnect(
                eq(scannerMock),
                any()
            )
        } answers {}
    }

    private fun setupScannerWithBatteryInfo(batteryInfo: BatteryInfo) {
        every { scannerMock.getBatteryPercentCharge() } returns Single.just(batteryInfo.charge)
        every { scannerMock.getBatteryVoltageMilliVolts() } returns Single.just(batteryInfo.voltage)
        every { scannerMock.getBatteryCurrentMilliAmps() } returns Single.just(batteryInfo.current)
        every { scannerMock.getBatteryTemperatureDeciKelvin() } returns Single.just(batteryInfo.temperature)
    }

    @Test
    fun ifNoAvailableVersions_completesNormally() = runBlocking {
        every { scannerMock.getVersionInformation() } returns Single.just(SCANNER_VERSION_LOW)
        every { vero2Configuration.firmwareVersions } returns mapOf()
        every { batteryLevelChecker.isLowBattery() } returns false
        setupScannerWithBatteryInfo(HIGH_BATTERY_INFO)


        scannerInitialSetupHelper.setupScannerWithOtaCheck(scannerMock, MAC_ADDRESS, {}, {})


        coVerify(exactly = 0) { connectionHelperMock.reconnect(any(), any()) }
    }

    @Test
    fun ifVersionsContainsUnknowns_throwsCorrectOtaAvailableException() = runBlocking {
        every { scannerMock.getVersionInformation() } returns Single.just(SCANNER_VERSION_LOW)
        every { vero2Configuration.firmwareVersions } returns mapOf(
            HARDWARE_VERSION to Vero2Configuration.Vero2FirmwareVersions(
                CYPRESS_VERSION_STRING, "", ""
            )
        )
        every { batteryLevelChecker.isLowBattery() } returns false
        setupScannerWithBatteryInfo(HIGH_BATTERY_INFO)

        val exception = assertThrows<OtaAvailableException> {
            scannerInitialSetupHelper.setupScannerWithOtaCheck(scannerMock, MAC_ADDRESS, {}, {})

        }

        assertThat(exception.availableOtas).isEqualTo(listOf(AvailableOta.CYPRESS))
    }

    @Test
    fun setupScannerWithOtaCheck_savesVersionAndBatteryInfo() = runBlocking {
        every { scannerMock.getVersionInformation() } returns Single.just(SCANNER_VERSION_LOW)
        every { vero2Configuration.firmwareVersions } returns mapOf(
            HARDWARE_VERSION to Vero2Configuration.Vero2FirmwareVersions(
                "", "", ""
            )
        )
        every { batteryLevelChecker.isLowBattery() } returns false
        setupScannerWithBatteryInfo(HIGH_BATTERY_INFO)

        var version: ScannerVersion? = null
        var batteryInfo: BatteryInfo? = null

        scannerInitialSetupHelper.setupScannerWithOtaCheck(
            scannerMock,
            MAC_ADDRESS,
            { version = it },
            { batteryInfo = it })

        assertThat(version).isEqualTo(SCANNER_VERSION_LOW.toScannerVersion())
        assertThat(batteryInfo).isEqualTo(HIGH_BATTERY_INFO)
    }

    @Test
    fun ifAvailableVersionMatchesExistingVersion_completesNormally() = runBlocking {
        every { scannerMock.getVersionInformation() } returns Single.just(SCANNER_VERSION_LOW)
        every { vero2Configuration.firmwareVersions } returns mapOf(
            HARDWARE_VERSION to Vero2Configuration.Vero2FirmwareVersions(
                SCANNER_VERSION_LOW.firmwareVersions.cypressFirmwareVersion.versionAsString,
                SCANNER_VERSION_LOW.firmwareVersions.stmFirmwareVersion.versionAsString,
                SCANNER_VERSION_LOW.firmwareVersions.un20AppVersion.versionAsString,
            )
        )
        every { batteryLevelChecker.isLowBattery() } returns false
        setupScannerWithBatteryInfo(HIGH_BATTERY_INFO)


        scannerInitialSetupHelper.setupScannerWithOtaCheck(scannerMock, MAC_ADDRESS, {}, {})

    }

    @Test
    fun ifAvailableVersionGreaterThanExistingVersion_throwsOtaAvailableExceptionAndReconnects() =
        runBlocking {
            every { scannerMock.getVersionInformation() } returns Single.just(SCANNER_VERSION_LOW)
            every { vero2Configuration.firmwareVersions } returns mapOf(
                HARDWARE_VERSION to Vero2Configuration.Vero2FirmwareVersions(
                    CYPRESS_VERSION_STRING, STM_VERSION_STRING, UN20_VERSION_STRING
                )
            )

            every { batteryLevelChecker.isLowBattery() } returns false
            setupScannerWithBatteryInfo(HIGH_BATTERY_INFO)

            val exception = assertThrows<OtaAvailableException> {
                scannerInitialSetupHelper.setupScannerWithOtaCheck(scannerMock, MAC_ADDRESS, {}, {})

            }

            assertThat(exception.availableOtas).isEqualTo(
                listOf(
                    AvailableOta.CYPRESS,
                    AvailableOta.STM,
                    AvailableOta.UN20
                )
            )
            coVerify { connectionHelperMock.reconnect(eq(scannerMock), any()) }
        }

    @Test
    fun ifAvailableVersionGreaterThanExistingVersion_lowScannerBattery_completesNormally() =
        runBlocking {
            every { scannerMock.getVersionInformation() } returns Single.just(SCANNER_VERSION_LOW)
            every { vero2Configuration.firmwareVersions } returns mapOf(
                HARDWARE_VERSION to Vero2Configuration.Vero2FirmwareVersions(
                    CYPRESS_VERSION_STRING, STM_VERSION_STRING, UN20_VERSION_STRING
                )
            )
            every { batteryLevelChecker.isLowBattery() } returns false
            setupScannerWithBatteryInfo(LOW_BATTERY_INFO)


            scannerInitialSetupHelper.setupScannerWithOtaCheck(scannerMock, MAC_ADDRESS, {}, {})

        }

    @Test
    fun ifAvailableVersionGreaterThanExistingVersion_lowPhoneBattery_completesNormally() =
        runBlocking {
            every { scannerMock.getVersionInformation() } returns Single.just(SCANNER_VERSION_LOW)
            every { vero2Configuration.firmwareVersions } returns mapOf(
                HARDWARE_VERSION to Vero2Configuration.Vero2FirmwareVersions(
                    CYPRESS_VERSION_STRING, STM_VERSION_STRING, UN20_VERSION_STRING
                )
            )
            every { batteryLevelChecker.isLowBattery() } returns true
            setupScannerWithBatteryInfo(HIGH_BATTERY_INFO)


            scannerInitialSetupHelper.setupScannerWithOtaCheck(scannerMock, MAC_ADDRESS, {}, {})

        }

    @Test
    fun ifAvailableVersionGreaterThanExistingVersion_stillSavesVersionAndBatteryInfo() =
        runBlocking {
            every { scannerMock.getVersionInformation() } returns Single.just(SCANNER_VERSION_LOW)
            every { vero2Configuration.firmwareVersions } returns mapOf(
                HARDWARE_VERSION to Vero2Configuration.Vero2FirmwareVersions(
                    CYPRESS_VERSION_STRING, STM_VERSION_STRING, UN20_VERSION_STRING
                )
            )
            every { batteryLevelChecker.isLowBattery() } returns false
            setupScannerWithBatteryInfo(HIGH_BATTERY_INFO)

            var version: ScannerVersion? = null
            var batteryInfo: BatteryInfo? = null


            val exception = assertThrows<OtaAvailableException> {
                scannerInitialSetupHelper.setupScannerWithOtaCheck(
                    scannerMock,
                    MAC_ADDRESS,
                    { version = it },
                    { batteryInfo = it })
            }

            assertThat(exception.availableOtas).isEqualTo(
                listOf(
                    AvailableOta.CYPRESS,
                    AvailableOta.STM,
                    AvailableOta.UN20
                )
            )
            coVerify { connectionHelperMock.reconnect(eq(scannerMock), any()) }

            assertThat(version).isEqualTo(SCANNER_VERSION_LOW.toScannerVersion())
            assertThat(batteryInfo).isEqualTo(HIGH_BATTERY_INFO)
        }

    companion object {

        const val MAC_ADDRESS = "mac address"
        private const val HARDWARE_VERSION = "E-1"

        val HIGH_BATTERY_INFO = BatteryInfo(80, 2, 3, 4)
        val LOW_BATTERY_INFO = BatteryInfo(10, 2, 3, 4)

        private const val STM_VERSION_STRING = ("14.E-1.16")
        private const val CYPRESS_VERSION_STRING = ("3.E-1.4")
        private const val UN20_VERSION_STRING = ("7.E-1.8")

        val SCANNER_VERSION_LOW = ScannerInformation(
            hardwareVersion = HARDWARE_VERSION,
            firmwareVersions = UnifiedVersionInformation(
                10L,
                CypressFirmwareVersion(12, 13, 14, 15),
                StmFirmwareVersion(1, 2, 3, 2),
                Un20AppVersion(5, 6, 7, 2)
            ).toExtendedVersionInfo()
        )


        val LOCAL_SCANNER_VERSION = mapOf(
            DownloadableFirmwareVersion.Chip.CYPRESS to setOf(CYPRESS_VERSION_STRING),
            DownloadableFirmwareVersion.Chip.STM to setOf(STM_VERSION_STRING),
            DownloadableFirmwareVersion.Chip.UN20 to setOf(UN20_VERSION_STRING)
        )
    }
}
