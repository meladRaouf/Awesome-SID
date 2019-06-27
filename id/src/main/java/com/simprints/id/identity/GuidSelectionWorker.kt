package com.simprints.id.identity

import android.annotation.SuppressLint
import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.simprints.id.Application
import com.simprints.id.services.GuidSelectionManager
import com.simprints.id.tools.extensions.parseAppConfirmation
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import timber.log.Timber
import javax.inject.Inject

class GuidSelectionWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    @Inject lateinit var guidSelectionManager: GuidSelectionManager

    override fun doWork(): Result {
        (applicationContext as Application).component.inject(this)
        handleGuidSelectionRequest()
        return Result.success()
    }

    @SuppressLint("CheckResult")
    private fun handleGuidSelectionRequest() {
        val request = inputData.parseAppConfirmation()
        guidSelectionManager.handleIdentityConfirmationRequest(request)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeBy(onComplete = {
                Timber.d("Added Guid Selection Event")
            }, onError = {
                Timber.e(it)
            })
    }

}
