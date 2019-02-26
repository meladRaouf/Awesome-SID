package com.simprints.id.data.db.remote.adapters

import com.google.firebase.database.ServerValue
import com.simprints.id.session.Session
import com.simprints.id.data.db.remote.models.fb_Session

fun Session.toFirebaseSession(): fb_Session =
    with(this) {
        fb_Session(sessionId,
            androidSdkVersion,
            deviceModel,
            deviceId,
            appVersionName,
            calloutAction,
            projectId,
            moduleId,
            userId,
            patientId,
            callingPackage,
            metadata,
            resultFormat,
            macAddress,
            scannerId,
            hardwareVersion,
            latitude,
            longitude,
            msSinceBootOnLoadEnd - msSinceBootOnSessionStart,
            msSinceBootOnMainStart - msSinceBootOnSessionStart,
            msSinceBootOnMatchStart - msSinceBootOnSessionStart,
            msSinceBootOnSessionEnd - msSinceBootOnSessionStart,
            ServerValue.TIMESTAMP)
    }
