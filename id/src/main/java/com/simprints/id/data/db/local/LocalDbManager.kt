package com.simprints.id.data.db.local

import com.google.gson.Gson
import com.google.gson.stream.JsonReader
import com.simprints.id.data.db.DataCallback
import com.simprints.id.data.db.local.models.rl_Person
import com.simprints.id.data.db.remote.models.fb_Person
import com.simprints.id.domain.Constants
import com.simprints.id.services.sync.SyncTaskParameters
import com.simprints.libcommon.Person
import io.reactivex.Completable
import io.realm.Realm
import io.realm.RealmConfiguration

interface LocalDbManager {

    // Lifecycle
    fun signInToLocal(projectId: String, localDbKey: LocalDbKey): Completable
    fun signOutOfLocal()
    fun isLocalDbInitialized(projectId: String): Boolean

    // Data transfer
    fun savePersonInLocal(fbPerson: fb_Person): Completable
    fun savePeopleFromStream(reader: JsonReader, gson: Gson, groupSync: Constants.GROUP, shouldStop: () -> Boolean)
    fun updatePersonInLocal(fbPerson: fb_Person): Completable

    fun loadPersonFromLocal(destinationList: MutableList<Person>, guid: String, callback: DataCallback)
    fun loadPeopleFromLocal(destinationList: MutableList<Person>, group: Constants.GROUP, userId: String, moduleId: String, callback: DataCallback?)
    fun getPeopleCountFromLocal(group: Constants.GROUP, userId: String, moduleId: String): Long
    fun getPeopleToUpSync(): ArrayList<rl_Person>
    fun getPeopleFor(syncParams: SyncTaskParameters): ArrayList<rl_Person>

    //Sync
    fun getSyncInfoFor(typeSync: Constants.GROUP): RealmSyncInfo?

    // Database instances
    fun getValidRealmConfig(): RealmConfiguration
    fun getRealmInstance(): Realm
}
