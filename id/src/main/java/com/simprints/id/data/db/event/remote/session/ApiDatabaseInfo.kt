package com.simprints.id.data.db.event.remote.session

import androidx.annotation.Keep
import com.simprints.id.data.db.event.domain.events.session.DatabaseInfo

@Keep
open class ApiDatabaseInfo(var recordCount: Int?,
                           var sessionCount: Int = 0) {
    constructor(databaseInfo: DatabaseInfo) :
        this(databaseInfo.recordCount, databaseInfo.sessionCount)
}
