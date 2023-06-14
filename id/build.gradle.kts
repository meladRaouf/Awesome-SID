plugins {
    id("simprints.android.application")
    id("kotlin-parcelize")

    id("simprints.ci.deploy")
    id("com.vanniktech.dependency.graph.generator")
}

android {
    namespace = "com.simprints.id"

    defaultConfig {
        ndk.abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64"))
    }

    buildTypes {
        getByName("release") {
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        getByName("staging") {
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        getByName("debug") {
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

dependencies {
    // ######################################################
    //                     (Dependencies)
    // ######################################################

    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    implementation(project(":core"))

    implementation(project(":infrauibase"))
    implementation(project(":infraevents"))
    implementation(project(":infraeventsync"))
    implementation(project(":infra:auth-logic"))
    implementation(project(":infra:auth-store"))
    implementation(project(":infra:project-security-store"))
    implementation(project(":clientapi"))
    implementation(project(":face"))
    implementation(project(":feature:login"))
    implementation(project(":feature:fetch-subject"))
    implementation(project(":feature:select-subject"))
    implementation(project(":feature:enrol-last-biometric"))
    implementation(project(":feature:setup"))
    implementation(project(":featuredashboard"))
    implementation(project(":feature:alert"))
    implementation(project(":featureexitform"))
    implementation(project(":featureconsent"))
    implementation(project(":fingerprint:controller"))
    implementation(project(":infraconfig"))
    implementation(project(":infraenrolmentrecords"))
    implementation(project(":infrarecentuseractivity"))
    implementation(project(":infraimages"))

    implementation(libs.kotlin.reflect)
    runtimeOnly(libs.kotlin.coroutinesAndroid)

    implementation(libs.androidX.core)
    implementation(libs.androidX.appcompat)
    implementation(libs.androidX.ui.activity)
    implementation(libs.androidX.ui.fragment)
    implementation(libs.androidX.ui.constraintlayout)
    implementation(libs.androidX.ui.cardview)
    implementation(libs.support.material)

    implementation(libs.androidX.lifecycle)
    implementation(libs.androidX.lifecycle.scope)
    implementation(libs.androidX.lifecycle.livedata.ktx)

    implementation(libs.workManager.work)

    implementation(libs.rxJava2.core)

    // ######################################################
    //                      Unit test
    // ######################################################

    testImplementation(project(":testtools"))
    testImplementation(project(":infraevents"))
    testImplementation(project(":infraeventsync"))
    testImplementation(project(":infralogging"))

    // ######################################################
    //                      Android test
    // ######################################################

    androidTestImplementation(project(":testtools"))
    androidTestImplementation(project(":fingerprint:infra:scannermock")) {
        exclude("org.robolectric")
    }
    androidTestUtil(libs.testing.androidX.orchestrator)
    androidTestImplementation(libs.testing.espresso.barista) {
        exclude("com.android.support")
        exclude("com.google.code.findbugs")
        exclude("org.jetbrains.kotlin")
        exclude("com.google.guava")
    }
}
