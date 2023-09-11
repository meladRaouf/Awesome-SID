package com.simprints.infra.realm.config

import androidx.annotation.Keep
import com.simprints.infra.realm.BuildConfig
import com.simprints.infra.realm.migration.RealmMigrations
import com.simprints.infra.realm.models.DbFaceSample
import com.simprints.infra.realm.models.DbFingerprintSample
import com.simprints.infra.realm.models.DbProject
import com.simprints.infra.realm.models.DbSubject
import io.realm.RealmConfiguration
import io.realm.annotations.RealmModule

@Keep
object RealmConfig {

    @Keep
    @RealmModule(
        classes = [
            DbFingerprintSample::class,
            DbFaceSample::class,
            DbSubject::class,
            DbProject::class
        ]
    )
    class Module

    private const val REALM_SCHEMA_VERSION: Long = 14

    fun get(databaseName: String, key: ByteArray, projectId: String): RealmConfiguration {
        val builder = RealmConfiguration
            .Builder()
            .name("$databaseName.realm")
            .schemaVersion(REALM_SCHEMA_VERSION)
            .migration(RealmMigrations(projectId))
            .modules(Module())

        if (BuildConfig.DB_ENCRYPTION)
            builder.encryptionKey(key)

        return builder.build()
    }
}
