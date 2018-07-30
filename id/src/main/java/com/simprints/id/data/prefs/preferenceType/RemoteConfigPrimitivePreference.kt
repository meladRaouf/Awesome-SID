package com.simprints.id.data.prefs.preferenceType

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.simprints.id.data.prefs.improvedSharedPreferences.ImprovedSharedPreferences
import com.simprints.id.exceptions.unsafe.NonPrimitiveTypeError
import com.simprints.id.exceptions.unsafe.PreferenceClassCastException
import kotlin.reflect.KProperty

class RemoteConfigPrimitivePreference<T : Any>(preferences: ImprovedSharedPreferences,
                                               private val remoteConfig: FirebaseRemoteConfig,
                                               remoteDefaultsMap: MutableMap<String, Any>,
                                               private val key: String,
                                               private val defValue: T) {

    private val storedDefaultValue = defValue
    private var storedValue by PrimitivePreference(preferences, key, defValue)

    init {
        remoteDefaultsMap[key] = storedDefaultValue
    }

    operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        try {
            @Suppress("UNCHECKED_CAST")
            val value = when (defValue) {
                is Boolean -> remoteConfig.getBoolean(key)
                is Long -> remoteConfig.getLong(key)
                is Int -> remoteConfig.getLong(key).toInt()
                is Double -> remoteConfig.getDouble(key)
                is Float -> remoteConfig.getDouble(key).toFloat()
                is String -> remoteConfig.getString(key)
                is ByteArray -> remoteConfig.getByteArray(key)
                else -> throw NonPrimitiveTypeError.forTypeOf(defValue)
            } as T
            storedValue = value
            return value
        } catch (e: ClassCastException) {
            throw PreferenceClassCastException.withKey(key)
        }
    }
}
