package com.simprints.id.services.scheduledSync.peopleUpsync

import com.google.common.truth.Truth.assertThat
import com.nhaarman.mockitokotlin2.any
import com.simprints.id.data.db.person.domain.Person
import com.simprints.id.data.db.person.local.PersonLocalDataSource
import com.simprints.id.data.db.person.remote.PersonRemoteDataSource
import com.simprints.id.data.db.syncstatus.upsyncinfo.UpSyncDao
import com.simprints.id.data.loginInfo.LoginInfoManager
import com.simprints.id.exceptions.safe.data.db.SimprintsInternalServerException
import com.simprints.id.exceptions.safe.sync.TransientSyncFailureException
import com.simprints.id.services.scheduledSync.peopleUpsync.uploader.PeopleUpSyncUploaderTask
import com.simprints.id.testtools.UnitTestConfig
import com.simprints.testtools.common.syntax.*
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.reactivex.Completable
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import java.util.*

class PeopleUpSyncUploaderTaskTest {

    private val loginInfoManager: LoginInfoManager = mock()
    private val personLocalDataSource: PersonLocalDataSource = mockk()
    private val personRemoteDataSource: PersonRemoteDataSource = mock()
    private val upSyncDao: UpSyncDao = mock()

    private val projectIdToSync = "projectIdToSync"
    private val userIdToSync = "userIdToSync"
    private val batchSize = 2

    private val task = PeopleUpSyncUploaderTask(
        loginInfoManager, personLocalDataSource, personRemoteDataSource,
        projectIdToSync, /*userIdToSync, */batchSize, upSyncDao // TODO: uncomment userId when multitenancy is properly implemented
    )

    private val differentProjectId = "differentProjectId"
//    private val differentUserId = "differentUserId" // TODO: uncomment userId when multitenancy is properly implemented

    private val notYetSyncedPerson1 = Person(
        "patientId1", "projectId", "userId", "moduleId", Date(1), null, true
    )
    private val notYetSyncedPerson2 = notYetSyncedPerson1.copy(patientId = "patientId2")
    private val notYetSyncedPerson3 = notYetSyncedPerson1.copy(patientId = "patientId3")

    private val syncedPerson1 = notYetSyncedPerson1.copy(toSync = false)
    private val syncedPerson2 = notYetSyncedPerson2.copy(toSync = false)
    private val syncedPerson3 = notYetSyncedPerson3.copy(toSync = false)

    @Before
    fun setUp() {
        UnitTestConfig(this)
            .coroutinesMainThread()
    }

    @Test
    fun userNotSignedIn1_shouldThrowIllegalStateException() {
        mockSignedInUser(differentProjectId, userIdToSync)

        runBlocking {
            assertThrows<IllegalStateException> {
                task.execute()
            }
        }
    }

    /* // TODO: uncomment userId when multitenancy is properly implemented
    @Test
    fun userNotSignedIn2_shouldThrowIllegalStateException() {
        mockSignedInUser(projectIdToSync, differentUserId)

        assertThrows<IllegalStateException> {
            task.execute()
        }
    }
    */

    @Test
    fun simprintsInternalServerException_shouldWrapInTransientSyncFailureException() {
        mockSignedInUser(projectIdToSync, userIdToSync)
        mockSuccessfulLocalPeopleQueries(listOf(notYetSyncedPerson1))
        whenever(personRemoteDataSource.uploadPeople(projectIdToSync, listOf(notYetSyncedPerson1)))
            .thenThrow(SimprintsInternalServerException())

        runBlocking {
            assertThrows<TransientSyncFailureException> {
                task.execute()
            }
        }
    }

    @Test
    fun singleBatchNoConcurrentWrite() =
        testSuccessfulUpSync(
            localQueryResults = arrayOf(
                listOf(notYetSyncedPerson1, notYetSyncedPerson2)
            ),
            expectedUploadBatches = arrayOf(
                listOf(notYetSyncedPerson1, notYetSyncedPerson2)
            ),
            expectedLocalUpdates = arrayOf(
                listOf(syncedPerson1, syncedPerson2)
            )
        )

    @Test
    fun multipleBatchesNoConcurrentWrite() =
        testSuccessfulUpSync(
            localQueryResults = arrayOf(
                listOf(notYetSyncedPerson1, notYetSyncedPerson2, notYetSyncedPerson3)
            ),
            expectedUploadBatches = arrayOf(
                listOf(notYetSyncedPerson1, notYetSyncedPerson2),
                listOf(notYetSyncedPerson3)
            ),
            expectedLocalUpdates = arrayOf(
                listOf(syncedPerson1, syncedPerson2),
                listOf(syncedPerson3)
            )
        )

    @Test
    fun singleBatchConcurrentWrite() =
        testSuccessfulUpSync(
            localQueryResults = arrayOf(
                listOf(notYetSyncedPerson1, notYetSyncedPerson2),
                listOf(notYetSyncedPerson3)
            ),
            expectedUploadBatches = arrayOf(
                listOf(notYetSyncedPerson1, notYetSyncedPerson2),
                listOf(notYetSyncedPerson3)
            ),
            expectedLocalUpdates = arrayOf(
                listOf(syncedPerson1, syncedPerson2),
                listOf(syncedPerson3)
            )
        )

    private fun testSuccessfulUpSync(
        localQueryResults: Array<List<Person>>,
        expectedUploadBatches: Array<List<Person>>,
        expectedLocalUpdates: Array<List<Person>>
    ) {
        mockSignedInUser(projectIdToSync, userIdToSync)
        mockSuccessfulLocalPeopleQueries(*localQueryResults)
        mockSuccessfulPeopleUploads(*expectedUploadBatches)
        mockSuccessfulLocalPeopleUpdates(*expectedLocalUpdates)
        mockSyncStatusModel()

        runBlocking {
            task.execute()
        }

        verifyLocalPeopleQueries(*localQueryResults)
        verifyPeopleUploads(*expectedUploadBatches)
        verifyLocalPeopleUpdates(*expectedLocalUpdates)
    }

    private fun mockSignedInUser(projectId: String, userId: String) {
        whenever(loginInfoManager.signedInProjectId).thenReturn(projectId)
        whenever(loginInfoManager.signedInUserId).thenReturn(userId)
    }

    private fun mockSuccessfulLocalPeopleQueries(vararg queryResults: List<Person>) {
        coEvery { personLocalDataSource.load(any()) } coAnswers {
            queryResults.fold(emptyList<Person>()) { aggr, new -> aggr + new }.toList().asFlow()
        }
    }

    private fun mockSuccessfulPeopleUploads(vararg batches: List<Person>) {
        whenever(personRemoteDataSource.uploadPeople(anyNotNull(), anyNotNull())).thenReturn(Completable.complete())
    }

    private fun mockSuccessfulLocalPeopleUpdates(vararg updates: List<Person>) {
        updates.forEach { update ->
            coEvery { personLocalDataSource.insertOrUpdate(any()) } returns Unit
        }
    }

    private fun mockSyncStatusModel() {
        whenever(upSyncDao.insertLastUpSyncTime(any())).then { }
    }

    private fun verifyLocalPeopleQueries(vararg queryResults: List<Person>) {
        coVerify(exactly = 1) {
            personLocalDataSource.load(withArg {
                assertThat(it.toSync).isEqualTo(true)
            })
        }
    }

    private fun verifyPeopleUploads(vararg batches: List<Person>) {
        batches.forEach { batch ->
            verifyOnce(personRemoteDataSource) { uploadPeople(projectIdToSync, batch) }
        }
    }

    private fun verifyLocalPeopleUpdates(vararg updates: List<Person>) {
        updates.forEach { update ->
            coVerify(exactly = 1) { personLocalDataSource.insertOrUpdate(update) }
        }
    }
}
