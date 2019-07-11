package com.simprints.fingerprint.orchestrator.task

import com.simprints.fingerprint.activities.collect.CollectFingerprintsActivity
import com.simprints.fingerprint.activities.collect.request.CollectFingerprintsTaskRequest
import com.simprints.fingerprint.activities.collect.result.CollectFingerprintsTaskResult
import com.simprints.fingerprint.activities.launch.LaunchActivity
import com.simprints.fingerprint.activities.launch.request.LaunchTaskRequest
import com.simprints.fingerprint.activities.launch.result.LaunchTaskResult
import com.simprints.fingerprint.activities.matching.MatchingActivity
import com.simprints.fingerprint.activities.matching.request.MatchingTaskRequest
import com.simprints.fingerprint.activities.matching.result.MatchingTaskResult

sealed class FingerprintTask(
    val createTaskRequest: () -> TaskRequest,
    val taskResultKey: String
) {

    abstract class RunnableTask(
        createTaskRequest: () -> TaskRequest,
        taskResultKey: String
    ) : FingerprintTask(createTaskRequest, taskResultKey) {

        abstract fun runTask(taskRequest: TaskRequest): TaskResult
    }

    abstract class ActivityTask(
        createTaskRequest: () -> TaskRequest,
        taskResultKey: String,
        val targetClass: Class<*>,
        val requestCode: RequestCode,
        val requestBundleKey: String,
        val resultBundleKey: String
    ) : FingerprintTask(createTaskRequest, taskResultKey)

    class Launch(createLaunchActRequest: () -> LaunchTaskRequest, actResultKey: String) :
        ActivityTask(
            createLaunchActRequest,
            actResultKey,
            LaunchActivity::class.java,
            RequestCode.LAUNCH,
            LaunchTaskRequest.BUNDLE_KEY,
            LaunchTaskResult.BUNDLE_KEY
        )

    class CollectFingerprints(createCollectFingerprintsActRequest: () -> CollectFingerprintsTaskRequest,
                              actResultKey: String) :
        ActivityTask(
            createCollectFingerprintsActRequest,
            actResultKey,
            CollectFingerprintsActivity::class.java,
            RequestCode.COLLECT,
            CollectFingerprintsTaskRequest.BUNDLE_KEY,
            CollectFingerprintsTaskResult.BUNDLE_KEY
        )

    class Matching(createMatchingActRequest: () -> MatchingTaskRequest, actResultKey: String) :
        ActivityTask(
            createMatchingActRequest,
            actResultKey,
            MatchingActivity::class.java,
            RequestCode.MATCHING,
            MatchingTaskRequest.BUNDLE_KEY,
            MatchingTaskResult.BUNDLE_KEY
        )
}
