package com.simprints.id.data.analytics.eventdata.models.remote.session

import androidx.annotation.Keep
import com.simprints.id.data.analytics.eventdata.models.domain.session.SessionEvents
import com.simprints.id.data.analytics.eventdata.models.remote.events.ApiEvent
import com.simprints.id.data.analytics.eventdata.models.remote.toApiEvent
import java.util.*

@Keep
open class ApiSessionEvents(var appVersionName: String,
                            var libVersionName: String,
                            var language: String,
                            var device: ApiDevice,
                            var startTime: Long = 0,
                            val id: String = UUID.randomUUID().toString(),
                            var events: Array<ApiEvent> = arrayOf(),
                            var relativeEndTime: Long = 0,
                            var relativeUploadTime: Long = 0,
                            var databaseInfo: ApiDatabaseInfo,
                            var location: ApiLocation? = null,
                            var analyticsId: String? = null) {


    constructor(sessionDomain: SessionEvents) :
        this(sessionDomain.appVersionName,
            sessionDomain.libVersionName,
            // We are sending what we have in preference Manager as language
            // but that is not a BCP 47 standard
            sessionDomain.language.replace("-r", "-"),
            ApiDevice(sessionDomain.device),
            sessionDomain.startTime,
            sessionDomain.id,
            sessionDomain.events.map { it.toApiEvent() }.toTypedArray(),
            sessionDomain.relativeEndTime,
            sessionDomain.relativeUploadTime,
            ApiDatabaseInfo(sessionDomain.databaseInfo),
            sessionDomain.location?.let { ApiLocation(it) },
            sessionDomain.analyticsId)
}
