package com.simprints.id.sync

import com.google.common.truth.Truth.assertThat
import com.simprints.id.activities.ShadowAndroidXMultiDex
import com.simprints.id.services.scheduledSync.peopleDownSync.models.SubSyncScope
import com.simprints.id.services.scheduledSync.peopleDownSync.models.SyncScope
import com.simprints.id.testUtils.roboletric.TestApplication
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class, shadows = [ShadowAndroidXMultiDex::class])
class SyncScopeTest {

    @Test
    fun syncScopeUniqueKey(){
        val syncScope = SyncScope("projectId", "userId", setOf("moduleId"))
        assertThat(syncScope.uniqueKey).isEqualTo("projectId_userId_moduleId")
    }

    @Test
    fun syncScopeUniqueKeyWithNoUser(){
        val syncScope = SyncScope("projectId", null, setOf("moduleId"))
        assertThat(syncScope.uniqueKey).isEqualTo("projectId__moduleId")
    }

    @Test
    fun syncScopeUniqueKeyWithNoModule(){
        val syncScope = SyncScope("projectId", "userId", null)
        assertThat(syncScope.uniqueKey).isEqualTo("projectId_userId_")
    }

    @Test
    fun syncScopeUniqueKeyWithMultipleModules(){
        val syncScope = SyncScope("projectId", "userId", setOf("module1", "module2"))
        assertThat(syncScope.uniqueKey).isEqualTo("projectId_userId_module1_module2")
    }

    @Test
    fun syncScopeGeneratesSubScopes(){
        val syncScope = SyncScope("projectId", "userId", setOf("module1", "module2"))
        assertThat(syncScope.toSubSyncScopes().first()).isEqualTo(SubSyncScope("projectId", "userId", "module1"))
        assertThat(syncScope.toSubSyncScopes().last()).isEqualTo(SubSyncScope("projectId", "userId", "module2"))
    }

    @Test
    fun syncScopeGeneratesSubScopesWithNoModules(){
        val syncScope = SyncScope("projectId", "userId", null)
        assertThat(syncScope.toSubSyncScopes().first()).isEqualTo(SubSyncScope("projectId", "userId", null))
    }
}
