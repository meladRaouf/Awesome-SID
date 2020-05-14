package com.simprints.id.data.db.subject

import com.simprints.id.data.db.subjects_sync.down.domain.EventQuery
import com.simprints.id.data.db.subjects_sync.down.domain.SubjectsDownSyncOperation
import com.simprints.id.data.db.subjects_sync.down.domain.SubjectsDownSyncProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.ReceiveChannel

interface SubjectRepositoryDownSyncHelper {
    suspend fun performDownSyncWithProgress(scope: CoroutineScope,
                                            downSyncOperation: SubjectsDownSyncOperation,
                                            eventQuery: EventQuery): ReceiveChannel<SubjectsDownSyncProgress>
}
