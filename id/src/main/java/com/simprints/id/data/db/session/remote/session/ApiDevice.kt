package com.simprints.id.data.db.session.remote.session

import android.os.Build
import androidx.annotation.Keep
import com.simprints.id.data.db.session.domain.models.session.Device

@Keep
open class ApiDevice(
    var androidSdkVersion: String = Build.VERSION.SDK_INT.toString(),
    var deviceModel: String = Build.MANUFACTURER + "_" + Build.MODEL,
    var deviceId: String = "") {

    constructor(deviceId: Device) :
        this(deviceId.androidSdkVersion, deviceId.deviceModel, deviceId.deviceId)
}
