package com.simprints.infra.config.local

import androidx.datastore.core.DataStoreFactory
import androidx.datastore.dataStoreFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.simprints.core.tools.utils.LanguageHelper
import com.simprints.infra.config.domain.models.DeviceConfiguration
import com.simprints.infra.config.domain.models.Finger
import com.simprints.infra.config.domain.models.ProjectConfiguration
import com.simprints.infra.config.local.models.toDomain
import com.simprints.infra.config.local.serializer.DeviceConfigurationSerializer
import com.simprints.infra.config.local.serializer.ProjectConfigurationSerializer
import com.simprints.infra.config.local.serializer.ProjectSerializer
import com.simprints.infra.config.testtools.*
import com.simprints.testtools.common.syntax.assertThrows
import io.mockk.mockk
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConfigLocalDataSourceImplTest {

    companion object {
        private const val TEST_PROJECT_DATASTORE_NAME: String = "test_project_datastore"
        private const val TEST_CONFIG_DATASTORE_NAME: String = "test_config_datastore"
        private const val TEST_DEVICE_CONFIG_DATASTORE_NAME: String = "test_device_config_datastore"
    }

    private val testContext = InstrumentationRegistry.getInstrumentation().targetContext
    private val testProjectDataStore = DataStoreFactory.create(
        serializer = ProjectSerializer,
        produceFile = { testContext.dataStoreFile(TEST_PROJECT_DATASTORE_NAME) }
    )
    private val testProjectConfigDataStore = DataStoreFactory.create(
        serializer = ProjectConfigurationSerializer,
        produceFile = { testContext.dataStoreFile(TEST_CONFIG_DATASTORE_NAME) }
    )
    private val testDeviceConfigDataStore = DataStoreFactory.create(
        serializer = DeviceConfigurationSerializer,
        produceFile = { testContext.dataStoreFile(TEST_DEVICE_CONFIG_DATASTORE_NAME) }
    )
    private val configLocalDataSourceImpl =
        ConfigLocalDataSourceImpl(
            testProjectDataStore,
            testProjectConfigDataStore,
            testDeviceConfigDataStore
        )

    @Before
    fun setup() {
        LanguageHelper.init(mockk(relaxed = true))
    }

    @Test
    fun `should throw a NoSuchElementException when there is no project`() = runTest(
        UnconfinedTestDispatcher()
    ) {
        assertThrows<NoSuchElementException> {
            configLocalDataSourceImpl.getProject()
        }
    }

    @Test
    fun `should save the project correctly`() = runTest(UnconfinedTestDispatcher()) {
        val projectToSave = project

        configLocalDataSourceImpl.saveProject(projectToSave)
        val savedProject = configLocalDataSourceImpl.getProject()

        assertThat(savedProject).isEqualTo(projectToSave)
    }

    @Test
    fun `should clear the project correctly`() = runTest(UnconfinedTestDispatcher()) {
        configLocalDataSourceImpl.saveProject(project)
        configLocalDataSourceImpl.clearProject()

        assertThrows<NoSuchElementException> { configLocalDataSourceImpl.getProject() }
    }

    @Test
    fun `should save the project configuration and update the device configuration correctly`() =
        runTest(UnconfinedTestDispatcher()) {
            val projectConfigurationToSave = projectConfiguration

            configLocalDataSourceImpl.saveProjectConfiguration(projectConfigurationToSave)
            val savedProjectConfiguration = configLocalDataSourceImpl.getProjectConfiguration()
            val updatedDeviceConfiguration = configLocalDataSourceImpl.getDeviceConfiguration()
            val expectedDeviceConfiguration = DeviceConfiguration(
                language = projectConfiguration.general.defaultLanguage,
                selectedModules = listOf(),
                fingersToCollect = projectConfiguration.fingerprint!!.fingersToCapture,
                lastInstructionId = ""
            )
            assertThat(savedProjectConfiguration).isEqualTo(projectConfiguration)
            assertThat(updatedDeviceConfiguration).isEqualTo(expectedDeviceConfiguration)
        }

    @Test
    fun `should save the project configuration and only update the device configuration fingersToCollect if the device configuration has been overwritten for language`() =
        runTest(UnconfinedTestDispatcher()) {
            configLocalDataSourceImpl.updateDeviceConfiguration {
                it.apply {
                    it.language = "fr"
                    it.selectedModules = listOf("module1")
                }
            }
            val projectConfigurationToSave = projectConfiguration

            configLocalDataSourceImpl.saveProjectConfiguration(projectConfigurationToSave)
            val savedProjectConfiguration = configLocalDataSourceImpl.getProjectConfiguration()
            val updatedDeviceConfiguration = configLocalDataSourceImpl.getDeviceConfiguration()
            val expectedDeviceConfiguration = DeviceConfiguration(
                language = "fr",
                selectedModules = listOf("module1"),
                fingersToCollect = projectConfiguration.fingerprint!!.fingersToCapture,
                lastInstructionId = ""
            )
            assertThat(savedProjectConfiguration).isEqualTo(projectConfiguration)
            assertThat(updatedDeviceConfiguration).isEqualTo(expectedDeviceConfiguration)
        }

    @Test
    fun `should save the project configuration and only update the device configuration language if the device configuration has been overwritten for fingersToCollect`() =
        runTest(UnconfinedTestDispatcher()) {
            configLocalDataSourceImpl.updateDeviceConfiguration {
                it.apply {
                    it.fingersToCollect = listOf(Finger.LEFT_THUMB)
                    it.selectedModules = listOf("module1")
                }
            }
            val projectConfigurationToSave = projectConfiguration

            configLocalDataSourceImpl.saveProjectConfiguration(projectConfigurationToSave)
            val savedProjectConfiguration = configLocalDataSourceImpl.getProjectConfiguration()
            val updatedDeviceConfiguration = configLocalDataSourceImpl.getDeviceConfiguration()
            val expectedDeviceConfiguration = DeviceConfiguration(
                language = projectConfiguration.general.defaultLanguage,
                selectedModules = listOf("module1"),
                fingersToCollect = listOf(Finger.LEFT_THUMB),
                lastInstructionId = ""
            )
            assertThat(savedProjectConfiguration).isEqualTo(projectConfiguration)
            assertThat(updatedDeviceConfiguration).isEqualTo(expectedDeviceConfiguration)
        }

    @Test
    fun `should save the project configuration and update the device configuration correctly with an empty list of fingersToCollect if fingerprint config is missing`() =
        runTest(UnconfinedTestDispatcher()) {
            val projectConfigurationToSave = ProjectConfiguration(
                "projectId",
                generalConfiguration,
                faceConfiguration,
                null,
                consentConfiguration,
                identificationConfiguration,
                synchronizationConfiguration
            )

            configLocalDataSourceImpl.saveProjectConfiguration(projectConfigurationToSave)
            val savedProjectConfiguration = configLocalDataSourceImpl.getProjectConfiguration()
            val updatedDeviceConfiguration = configLocalDataSourceImpl.getDeviceConfiguration()
            val expectedDeviceConfiguration = DeviceConfiguration(
                language = generalConfiguration.defaultLanguage,
                selectedModules = listOf(),
                fingersToCollect = listOf(),
                lastInstructionId = ""
            )
            assertThat(savedProjectConfiguration).isEqualTo(projectConfigurationToSave)
            assertThat(updatedDeviceConfiguration).isEqualTo(expectedDeviceConfiguration)
        }

    @Test
    fun `should return the default configuration when there is not configuration saved`() =
        runTest(UnconfinedTestDispatcher()) {
            val projectConfiguration = configLocalDataSourceImpl.getProjectConfiguration()
            assertThat(projectConfiguration).isEqualTo(ConfigLocalDataSourceImpl.defaultProjectConfiguration.toDomain())
        }

    @Test
    fun `should clear the project configuration correctly`() = runTest(UnconfinedTestDispatcher()) {
        configLocalDataSourceImpl.saveProjectConfiguration(projectConfiguration)
        configLocalDataSourceImpl.clearProjectConfiguration()

        val savedProjectConfiguration = configLocalDataSourceImpl.getProjectConfiguration()
        assertThat(savedProjectConfiguration.projectId).isEqualTo("")
    }

    @Test
    fun `should update the device configuration correctly`() = runTest(UnconfinedTestDispatcher()) {
        configLocalDataSourceImpl.updateDeviceConfiguration {
            it.apply {
                it.language = "fr"
                it.fingersToCollect = listOf(Finger.LEFT_THUMB)
            }
        }
        val savedDeviceConfiguration = configLocalDataSourceImpl.getDeviceConfiguration()
        val expectedDeviceConfiguration =
            DeviceConfiguration("fr", listOf(), listOf(Finger.LEFT_THUMB), "")

        assertThat(savedDeviceConfiguration).isEqualTo(expectedDeviceConfiguration)
    }

    @Test
    fun `should update the fingers to collect in the device configuration correctly`() =
        runTest(UnconfinedTestDispatcher()) {
            configLocalDataSourceImpl.updateDeviceConfiguration {
                it.apply {
                    it.language = "fr"
                    it.fingersToCollect = listOf(Finger.LEFT_THUMB)
                }
            }
            var savedDeviceConfiguration = configLocalDataSourceImpl.getDeviceConfiguration()
            var expectedDeviceConfiguration =
                DeviceConfiguration("fr", listOf(), listOf(Finger.LEFT_THUMB), "")

            assertThat(savedDeviceConfiguration).isEqualTo(expectedDeviceConfiguration)

            configLocalDataSourceImpl.updateDeviceConfiguration {
                it.apply {
                    it.language = "fr"
                    it.fingersToCollect = listOf(Finger.LEFT_THUMB, Finger.LEFT_INDEX_FINGER)
                }
            }
            savedDeviceConfiguration = configLocalDataSourceImpl.getDeviceConfiguration()
            expectedDeviceConfiguration =
                DeviceConfiguration(
                    "fr",
                    listOf(),
                    listOf(Finger.LEFT_THUMB, Finger.LEFT_INDEX_FINGER),
                    ""
                )

            assertThat(savedDeviceConfiguration).isEqualTo(expectedDeviceConfiguration)
        }

    @Test
    fun `should clear the device configuration correctly`() = runTest(UnconfinedTestDispatcher()) {
        configLocalDataSourceImpl.updateDeviceConfiguration { it.apply { it.language = "fr" } }
        configLocalDataSourceImpl.clearDeviceConfiguration()

        val savedDeviceConfiguration = configLocalDataSourceImpl.getDeviceConfiguration()
        assertThat(savedDeviceConfiguration.language).isEqualTo("")
    }
}
