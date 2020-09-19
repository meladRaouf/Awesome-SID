package com.simprints.id.activities.settings.syncinformation

import androidx.lifecycle.*
import com.simprints.id.activities.settings.syncinformation.SyncInformationActivity.ViewState.SyncDataFetched
import com.simprints.id.activities.settings.syncinformation.modulecount.ModuleCount
import com.simprints.id.data.db.common.models.SubjectsCount
import com.simprints.id.data.db.subjects_sync.down.SubjectsDownSyncScopeRepository
import com.simprints.id.data.db.subject.SubjectRepository
import com.simprints.id.data.db.subject.local.SubjectLocalDataSource
import com.simprints.id.data.images.repository.ImageRepository
import com.simprints.id.data.prefs.PreferencesManager
import com.simprints.id.services.scheduledSync.subjects.master.SubjectsSyncManager
import com.simprints.id.services.scheduledSync.subjects.master.models.SubjectsDownSyncSetting.EXTRA
import com.simprints.id.services.scheduledSync.subjects.master.models.SubjectsDownSyncSetting.ON
import com.simprints.id.services.scheduledSync.subjects.master.models.SubjectsSyncState
import com.simprints.id.services.scheduledSync.subjects.master.models.SubjectsSyncWorkerState
import kotlinx.coroutines.launch

class SyncInformationViewModel(private val subjectRepository: SubjectRepository,
                               private val subjectLocalDataSource: SubjectLocalDataSource,
                               private val preferencesManager: PreferencesManager,
                               private val projectId: String,
                               private val subjectsDownSyncScopeRepository: SubjectsDownSyncScopeRepository,
                               private val imageRepository: ImageRepository,
                               private val subjectsSyncManager: SubjectsSyncManager) : ViewModel() {

    fun getViewStateLiveData(): LiveData<SyncInformationActivity.ViewState> = viewStateLiveData

    private val viewStateLiveData = MediatorLiveData<SyncInformationActivity.ViewState>()

    fun updateSyncInfo() {
        viewStateLiveData.addSource(subjectsSyncManager.getLastSyncState().map { it.isRunning() }) {
            viewModelScope.launch {
                if (it) {
                    viewStateLiveData.value = SyncInformationActivity.ViewState.LoadingState.Syncing
                } else {
                    viewStateLiveData.value = SyncInformationActivity.ViewState.LoadingState.Calculating
                    viewStateLiveData.value = fetchRecords()
                }
            }
        }
    }

    private suspend fun fetchRecords(): SyncDataFetched {
        val recordsInLocalCount = fetchLocalRecordCount()
        val imagesToUploadCount = fetchAndUpdateImagesToUploadCount()
        val recordsToUpSyncCount = fetchAndUpdateRecordsToUpSyncCount()
        val modulesCount = fetchAndUpdateSelectedModulesCount()
        val subjectCounts = fetchRecordsToCreateAndDeleteCountOrNull()
        return SyncDataFetched(
            recordsInLocal = recordsInLocalCount,
            recordsToDownSync = subjectCounts?.created,
            recordsToUpSync = recordsToUpSyncCount,
            recordsToDelete = subjectCounts?.deleted,
            imagesToUpload = imagesToUploadCount,
            moduleCounts = modulesCount
        )
    }

    private suspend fun fetchLocalRecordCount() =
        subjectLocalDataSource.count(SubjectLocalDataSource.Query(projectId = projectId))

    private fun fetchAndUpdateImagesToUploadCount() = imageRepository.getNumberOfImagesToUpload()

    private suspend fun fetchAndUpdateRecordsToUpSyncCount() =
        subjectLocalDataSource.count(SubjectLocalDataSource.Query(toSync = true))

    private suspend fun fetchRecordsToCreateAndDeleteCountOrNull(): SubjectsCount? {
        return if(isDownSyncAllowed()) {
            fetchAndUpdateRecordsToDownSyncAndDeleteCount()
        } else {
            null
        }
    }

    private fun isDownSyncAllowed() = with(preferencesManager) {
        subjectsDownSyncSetting == ON || subjectsDownSyncSetting == EXTRA
    }

    private suspend fun fetchAndUpdateRecordsToDownSyncAndDeleteCount(): SubjectsCount? {
        return try {
            val downSyncScope = subjectsDownSyncScopeRepository.getDownSyncScope()
            subjectRepository.countToDownSync(downSyncScope)
        } catch (t: Throwable) {
            t.printStackTrace()
            null
        }
    }

    private suspend fun fetchAndUpdateSelectedModulesCount() = preferencesManager.selectedModules.map {
            ModuleCount(it,
                subjectLocalDataSource.count(SubjectLocalDataSource.Query(projectId = projectId, moduleId = it)))
        }

    private fun SubjectsSyncState.isRunning(): Boolean {
        val downSyncStates = downSyncWorkersInfo
        val upSyncStates = upSyncWorkersInfo
        val allSyncStates = downSyncStates + upSyncStates
        return allSyncStates.any {
            it.state is SubjectsSyncWorkerState.Running || it.state is SubjectsSyncWorkerState.Enqueued
        }
    }
}
