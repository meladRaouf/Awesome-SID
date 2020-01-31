package com.simprints.id.testtools

import androidx.test.core.app.ApplicationProvider
import com.simprints.id.Application
import com.simprints.id.commontesttools.di.TestAppModule
import com.simprints.id.commontesttools.di.TestDataModule
import com.simprints.id.commontesttools.di.TestPreferencesModule
import com.simprints.id.commontesttools.di.TestSyncModule
import com.simprints.id.testtools.di.AppComponentForAndroidTests
import com.simprints.id.testtools.di.DaggerAppComponentForAndroidTests
import com.simprints.testtools.common.di.injectClassFromComponent
import com.squareup.rx2.idler.Rx2Idler
import io.reactivex.android.plugins.RxAndroidPlugins
import io.reactivex.plugins.RxJavaPlugins
import io.realm.Realm

class AndroidTestConfig<T : Any>(
    private val test: T,
    private val appModule: TestAppModule? = null,
    private val dataModule: TestDataModule? = null,
    private val preferencesModule: TestPreferencesModule? = null,
    private val syncModule: TestSyncModule? = null
) {

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var testAppComponent: AppComponentForAndroidTests

    fun fullSetup() =
        initAndInjectComponent()
            .initRxIdler()
            .initRealm()
            .initModules()

    private fun initRxIdler() = also {
        RxJavaPlugins.setInitComputationSchedulerHandler(Rx2Idler.create("RxJava 2.x Computation Scheduler"))
        RxJavaPlugins.setInitIoSchedulerHandler(Rx2Idler.create("RxJava 2.x Io Scheduler"))
        RxJavaPlugins.setInitNewThreadSchedulerHandler(Rx2Idler.create("RxJava 2.x New Thread Scheduler"))
        RxJavaPlugins.setInitSingleSchedulerHandler(Rx2Idler.create("RxJava 2.x Single Scheduler"))
        RxAndroidPlugins.setInitMainThreadSchedulerHandler(Rx2Idler.create("RxJava 2.x Main Scheduler"))

    }

    fun initAndInjectComponent() =
        initComponent().inject()

    private fun initComponent() = also {

        testAppComponent = DaggerAppComponentForAndroidTests.builder()
            .application(app)
            .appModule(appModule ?: TestAppModule(app))
            .dataModule(dataModule ?: TestDataModule())
            .preferencesModule(preferencesModule ?: TestPreferencesModule())
            .syncModule(syncModule ?: TestSyncModule())
            .build()

        app.component = testAppComponent
    }

    private fun inject() = also {
        injectClassFromComponent(testAppComponent, test)
    }

    fun initRealm() = also {
        Realm.init(app)
    }

    fun initModules() = also {
        app.initModules()
    }
}
