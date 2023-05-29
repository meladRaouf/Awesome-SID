package com.simprints.face

import com.simprints.face.controllers.core.events.FaceSessionEventsManager
import com.simprints.face.controllers.core.events.FaceSessionEventsManagerImpl
import com.simprints.face.controllers.core.flow.MasterFlowManager
import com.simprints.face.controllers.core.flow.MasterFlowManagerImpl
import com.simprints.face.controllers.core.image.FaceImageManager
import com.simprints.face.controllers.core.image.FaceImageManagerImpl
import com.simprints.face.controllers.core.repository.FaceDbManager
import com.simprints.face.controllers.core.repository.FaceDbManagerImpl
import com.simprints.face.controllers.core.timehelper.FaceTimeHelper
import com.simprints.face.controllers.core.timehelper.FaceTimeHelperImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class FaceModule {

    @Binds
    abstract fun provideFaceImageManager(impl: FaceImageManagerImpl): FaceImageManager

    @Binds
    abstract fun provideFaceSessionEventsManager(impl: FaceSessionEventsManagerImpl): FaceSessionEventsManager

    @Binds
    abstract fun provideFaceTimeHelper(impl: FaceTimeHelperImpl): FaceTimeHelper

    @Binds
    abstract fun provideFaceDbManager(impl: FaceDbManagerImpl): FaceDbManager

    @Binds
    abstract fun provideMasterFlowManager(impl: MasterFlowManagerImpl): MasterFlowManager
}
