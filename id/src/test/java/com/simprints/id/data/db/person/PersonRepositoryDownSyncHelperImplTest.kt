package com.simprints.id.data.db.person

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth
import com.google.gson.JsonSyntaxException
import com.simprints.id.commontesttools.DefaultTestConstants
import com.simprints.id.commontesttools.EnrolmentRecordsGeneratorUtils.getRandomEnrolmentEvents
import com.simprints.id.data.db.people_sync.down.PeopleDownSyncScopeRepository
import com.simprints.id.data.db.people_sync.down.domain.PeopleDownSyncOperation
import com.simprints.id.data.db.people_sync.down.domain.PeopleDownSyncOperationFactoryImpl
import com.simprints.id.data.db.person.PersonRepositoryDownSyncHelperImpl.Companion.BATCH_SIZE_FOR_DOWNLOADING
import com.simprints.id.data.db.person.domain.personevents.EventPayloadType.*
import com.simprints.id.data.db.person.local.PersonLocalDataSource
import com.simprints.id.data.db.person.remote.EventRemoteDataSource
import com.simprints.id.data.db.person.remote.models.personevents.ApiEvent
import com.simprints.id.data.db.person.remote.models.personevents.fromDomainToApi
import com.simprints.id.domain.modality.Modes
import com.simprints.id.testtools.UnitTestConfig
import com.simprints.id.tools.TimeHelperImpl
import com.simprints.id.tools.json.SimJsonHelper
import com.simprints.testtools.common.channel.testChannel
import com.simprints.testtools.common.syntax.assertThrows
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.InputStream
import kotlin.math.ceil

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class PersonRepositoryDownSyncHelperImplTest {

    val builder = PeopleDownSyncOperationFactoryImpl()
    private val modes = listOf(Modes.FACE, Modes.FINGERPRINT)
    private val projectSyncOp = PeopleDownSyncOperation(
        DefaultTestConstants.DEFAULT_PROJECT_ID,
        null,
        null,
        listOf(Modes.FINGERPRINT),
        null
    )

    private val userSyncOp = PeopleDownSyncOperation(
        DefaultTestConstants.DEFAULT_PROJECT_ID,
        DefaultTestConstants.DEFAULT_USER_ID,
        null,
        listOf(Modes.FINGERPRINT),
        null
    )

    private val moduleSyncOp = PeopleDownSyncOperation(
        DefaultTestConstants.DEFAULT_PROJECT_ID,
        null,
        DefaultTestConstants.DEFAULT_MODULE_ID,
        listOf(Modes.FINGERPRINT),
        null
    )

    @RelaxedMockK lateinit var personLocalDataSource: PersonLocalDataSource
    @RelaxedMockK lateinit var eventRemoteDataSource: EventRemoteDataSource
    @RelaxedMockK lateinit var peopleDownSyncScopeRepository: PeopleDownSyncScopeRepository
    val timeHelper = TimeHelperImpl()

    @Before
    fun setUp() {
        UnitTestConfig(this)
            .coroutinesMainThread()
        MockKAnnotations.init(this)
    }


    @Test
    fun downloadEventsForGlobalSync_shouldSuccess() {
        runBlocking {
            val nEventsToDownload = 407
            val nEventsToDelete = 0
            val nEventsToUpdate = 0

            runDownSyncAndVerifyConditions(nEventsToDownload, nEventsToDelete, nEventsToUpdate, projectSyncOp)
        }
    }

    @Test
    fun downloadEventsForUserSync_shouldSuccess() {
        runBlocking {
            val nEventsToDownload = 513
            val nEventsToDelete = 0
            val nEventsToUpdate = 0

            runDownSyncAndVerifyConditions(nEventsToDownload, nEventsToDelete, nEventsToUpdate, userSyncOp)
        }
    }

    @Test
    fun downloadEventsForModuleSync_shouldSuccess() {
        runBlocking {
            val nPeopleToDownload = 513
            val nPeopleToDelete = 0
            val nEventsToUpdate = 0

            runDownSyncAndVerifyConditions(nPeopleToDownload, nPeopleToDelete, nEventsToUpdate, moduleSyncOp)
        }
    }

    @Test
    fun deleteEventsForGlobalSync_shouldSuccess() {
        runBlocking {
            val nEventsToDownload = 0
            val nEventsToDelete = 300
            val nEventsToUpdate = 0

            runDownSyncAndVerifyConditions(nEventsToDownload, nEventsToDelete, nEventsToUpdate, projectSyncOp)
        }
    }

    @Test
    fun deleteEventsForUserSync_shouldSuccess() {
        runBlocking {
            val nEventsToDownload = 0
            val nEventsToDelete = 212
            val nEventsToUpdate = 0

            runDownSyncAndVerifyConditions(nEventsToDownload, nEventsToDelete, nEventsToUpdate, userSyncOp)
        }
    }

    @Test
    fun deleteEventsForModuleSync_shouldSuccess() {
        runBlocking {
            val nEventsToDownload = 0
            val nEventsToDelete = 123
            val nEventsToUpdate = 0

            runDownSyncAndVerifyConditions(nEventsToDownload, nEventsToDelete, nEventsToUpdate, moduleSyncOp)
        }
    }

    @Test
    fun updateEventsForGlobalSync_shouldSuccess() {
        runBlocking {
            val nEventsToDownload = 0
            val nEventsToDelete = 0
            val nEventsToUpdate = 300

            runDownSyncAndVerifyConditions(nEventsToDownload, nEventsToDelete, nEventsToUpdate, projectSyncOp)
        }
    }

    @Test
    fun updateEventsForUserSync_shouldSuccess() {
        runBlocking {
            val nEventsToDownload = 0
            val nEventsToDelete = 0
            val nEventsToUpdate = 212

            runDownSyncAndVerifyConditions(nEventsToDownload, nEventsToDelete, nEventsToUpdate, userSyncOp)
        }
    }

    @Test
    fun updateEventsForModuleSync_shouldSuccess() {
        runBlocking {
            val nEventsToDownload = 0
            val nEventsToDelete = 0
            val nEventsToUpdate = 123

            runDownSyncAndVerifyConditions(nEventsToDownload, nEventsToDelete, nEventsToUpdate, moduleSyncOp)
        }
    }

    @Test
    fun downloadEvents_eventSerializationFails_shouldTriggerOnError() {
        runBlocking {
            val nEventsToDownload = 499
            val projectDownSyncOp = builder.buildProjectSyncOperation(DefaultTestConstants.DEFAULT_PROJECT_ID, modes, null)
            mockEventRemoteDataSourceWithIncorrectModels(nEventsToDownload)

            val downSyncHelper =
                PersonRepositoryDownSyncHelperImpl(personLocalDataSource, eventRemoteDataSource,
                    peopleDownSyncScopeRepository, timeHelper)

            assertThrows<IllegalStateException> {
                withContext(Dispatchers.IO) {
                    downSyncHelper.performDownSyncWithProgress(this, projectDownSyncOp, mockk(relaxed = true)).testChannel()
                }
            }
        }
    }

    @Test
    fun downSyncRequestFailsDueToMalformedJson_shouldSaveTheWellFormedElements() {
        runBlocking {
            val nEventsToDownload = 5
            mockEventRemoteDataSourceWithOneMalformedJson(nEventsToDownload)

            val downSyncHelper =
                PersonRepositoryDownSyncHelperImpl(personLocalDataSource, eventRemoteDataSource,
                    peopleDownSyncScopeRepository, timeHelper)

            assertThrows<JsonSyntaxException> {
                withContext(Dispatchers.IO) {
                    downSyncHelper.performDownSyncWithProgress(this, projectSyncOp, mockk(relaxed = true)).testChannel()
                }
            }

            coVerify(exactly = 1) {
                personLocalDataSource.insertOrUpdate(match {
                    Truth.assertThat(it).hasSize(4)
                    true
                })
            }
        }
    }

    private fun runDownSyncAndVerifyConditions(nEventsToDownload: Int,
                                               nEventsToDelete: Int,
                                               nEventsToUpdate: Int,
                                               syncOp: PeopleDownSyncOperation) {

        runBlocking {
            mockEventRemoteDataSource(nEventsToDownload, nEventsToDelete, nEventsToUpdate)
            mockDownSyncScopeRepository()

            val numberOfBatchesToUpdate = calculateCorrectNumberOfBatches(nEventsToUpdate)
            val numberOfBatchesToSave = calculateCorrectNumberOfBatches(nEventsToDownload) + numberOfBatchesToUpdate
            val numberOfBatchesToDelete = calculateCorrectNumberOfBatches(nEventsToDelete) + numberOfBatchesToUpdate

            val downSyncHelper =
                PersonRepositoryDownSyncHelperImpl(personLocalDataSource, eventRemoteDataSource,
                    peopleDownSyncScopeRepository, timeHelper)

            withContext(Dispatchers.IO) {
                downSyncHelper.performDownSyncWithProgress(this, syncOp, mockk()).testChannel()
                coVerify(exactly = numberOfBatchesToSave) { personLocalDataSource.insertOrUpdate(any()) }
                coVerify(exactly = numberOfBatchesToDelete) { personLocalDataSource.delete(any()) }
            }
        }
    }

    private fun mockEventRemoteDataSource(nEventsToDownload: Int, nEventsToDelete: Int, nEventsToUpdate: Int) {
        val creationEvents = getRandomCreationEvents(nEventsToDownload)
        val deletionEvents = getRandomDeletionEvents(nEventsToDelete)
        val updateEvents = getRandomMoveEvents(nEventsToUpdate)

        coEvery { eventRemoteDataSource.getStreaming(any()) } returns buildResponse(deletionEvents + creationEvents + updateEvents)
    }

    private fun mockEventRemoteDataSourceWithIncorrectModels(nEventsToDownload: Int) {
        val creationEvents = getRandomCreationEvents(nEventsToDownload)

        coEvery { eventRemoteDataSource.getStreaming(any()) } returns buildIncorrectResponse(creationEvents)
    }

    private fun mockEventRemoteDataSourceWithOneMalformedJson(nEventsToDownload: Int) {
        val creationEvents = getRandomCreationEvents(nEventsToDownload)

        coEvery { eventRemoteDataSource.getStreaming(any()) } returns buildMalformedResponse(creationEvents)
    }

    private fun getRandomCreationEvents(nEventsToDownload: Int) =
        getRandomEnrolmentEvents(nEventsToDownload, DefaultTestConstants.DEFAULT_PROJECT_ID,
            DefaultTestConstants.DEFAULT_USER_ID, DefaultTestConstants.DEFAULT_MODULE_ID,
            ENROLMENT_RECORD_CREATION).map { it.fromDomainToApi() }

    private fun getRandomDeletionEvents(nEventsToDelete: Int) =
        getRandomEnrolmentEvents(nEventsToDelete, DefaultTestConstants.DEFAULT_PROJECT_ID,
            DefaultTestConstants.DEFAULT_USER_ID, DefaultTestConstants.DEFAULT_MODULE_ID,
            ENROLMENT_RECORD_DELETION).map { it.fromDomainToApi() }

    private fun getRandomMoveEvents(nEventsToUpdate: Int) =
        getRandomEnrolmentEvents(nEventsToUpdate, DefaultTestConstants.DEFAULT_PROJECT_ID,
            DefaultTestConstants.DEFAULT_USER_ID, DefaultTestConstants.DEFAULT_MODULE_ID,
            ENROLMENT_RECORD_MOVE).map { it.fromDomainToApi() }

    private fun buildResponse(events: List<ApiEvent>): InputStream =
        SimJsonHelper.gson.toJson(events).toString().toResponseBody().byteStream()

    private fun buildIncorrectResponse(events: List<ApiEvent>): InputStream {
        val jsonString = SimJsonHelper.gson.toJson(events).toString()
        val incorrectJson = jsonString.replace("id", "incorrect_id")
        return incorrectJson.toResponseBody().byteStream()
    }

    private fun buildMalformedResponse(events: List<ApiEvent>): InputStream {
        val lastInstanceToReplace = "id"
        val jsonString = SimJsonHelper.gson.toJson(events).toString()
        val index = jsonString.lastIndexOf(lastInstanceToReplace)
        val beginString: String = jsonString.substring(0, index)
        val endString: String = jsonString.substring(index + lastInstanceToReplace.length)

        val responseString = beginString + "id\"" + endString

        return responseString.toResponseBody().byteStream()
    }

    private fun mockDownSyncScopeRepository() {
        coEvery { peopleDownSyncScopeRepository.insertOrUpdate(any())  } returns Unit
    }

    private fun calculateCorrectNumberOfBatches(nEvents: Int) =
        ceil(nEvents.toDouble() / BATCH_SIZE_FOR_DOWNLOADING.toDouble()).toInt()
}
