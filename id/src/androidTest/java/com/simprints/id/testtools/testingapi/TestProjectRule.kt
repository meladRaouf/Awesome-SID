package com.simprints.id.testtools.testingapi

import com.simprints.core.security.LocalDbKey
import com.simprints.core.tools.coroutines.DispatcherProvider
import com.simprints.id.commontesttools.AndroidDefaultTestConstants.DEFAULT_REALM_KEY
import com.simprints.id.testtools.testingapi.models.TestProject
import com.simprints.id.testtools.testingapi.models.TestProjectCreationParameters
import com.simprints.id.testtools.testingapi.remote.RemoteTestingManager
import org.junit.rules.TestRule
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Creates a new [TestProject] on the backend.
 * Usage as a [TestRule]:
 * @build:Rule val testProjectRule = TestProjectRule()
 */
class TestProjectRule(
    val dispatcher: DispatcherProvider,
    val testProjectCreationParameters: TestProjectCreationParameters = TestProjectCreationParameters()
) : TestWatcher() {

    lateinit var testProject: TestProject
    lateinit var localDbKey: LocalDbKey

    override fun starting(description: Description?) {

        testProject = RemoteTestingManager.create(dispatcher).createTestProject(testProjectCreationParameters)

        localDbKey = LocalDbKey(
            testProject.id,
            DEFAULT_REALM_KEY)
    }

    override fun finished(description: Description?) {
        RemoteTestingManager.create(dispatcher).deleteTestProject(testProject.id)
    }
}
