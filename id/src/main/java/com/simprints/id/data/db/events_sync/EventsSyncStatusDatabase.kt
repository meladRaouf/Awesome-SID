package com.simprints.id.data.db.events_sync

import android.content.Context
import androidx.annotation.Keep
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.simprints.id.data.db.common.room.Converters
import com.simprints.id.data.db.events_sync.down.local.DbEventsDownSyncOperation
import com.simprints.id.data.db.events_sync.down.local.EventDownSyncOperationLocalDataSource
import com.simprints.id.data.db.events_sync.up.local.DbEventsUpSyncOperation
import com.simprints.id.data.db.events_sync.up.local.EventsUpSyncOperationLocalDataSource

@Database(entities = [DbEventsDownSyncOperation::class, DbEventsUpSyncOperation::class], version = 2, exportSchema = false)
@TypeConverters(Converters::class)
@Keep
abstract class EventsSyncStatusDatabase : RoomDatabase() {

    abstract val downSyncOperationsDao: EventDownSyncOperationLocalDataSource

    abstract val upSyncOperationsDao: EventsUpSyncOperationLocalDataSource

    companion object {
        const val ROOM_DB_NAME = "room_db"

        fun getDatabase(context: Context): EventsSyncStatusDatabase = Room
            .databaseBuilder(context.applicationContext, EventsSyncStatusDatabase::class.java, ROOM_DB_NAME)
            .fallbackToDestructiveMigration()
            .allowMainThreadQueries()
            .build()
    }
}
