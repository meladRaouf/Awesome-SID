package com.simprints.id.data.db.session.domain.models.session

import android.os.Build
import androidx.annotation.Keep
import java.util.*

@Keep
open class Device(
    var androidSdkVersion: String = Build.VERSION.SDK_INT.toString(),
    var deviceModel: String = Build.MANUFACTURER + "_" + Build.MODEL,
    var deviceId: String = "",
    var id: String = UUID.randomUUID().toString())
