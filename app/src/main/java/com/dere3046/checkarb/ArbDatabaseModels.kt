package com.dere3046.checkarb

import android.os.Build
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ArbDatabase(
    @SerialName("device_name") val deviceName: String,
    @SerialName("device_order") val deviceOrder: Int,
    @SerialName("versions") val versions: Map<String, VersionInfo>
)

@Serializable
data class VersionInfo(
    @SerialName("arb") val arb: Int,
    @SerialName("major") val major: Int,
    @SerialName("minor") val minor: Int,
    @SerialName("md5") val md5: String?,
    @SerialName("first_seen") val firstSeen: String,
    @SerialName("last_checked") val lastChecked: String,
    @SerialName("status") val status: String,
    @SerialName("is_hardcoded") val isHardcoded: Boolean,
    @SerialName("regions") val regions: List<String>
)

data class DeviceArbInfo(
    val model: String,
    val deviceName: String,
    val buildNumber: String,
    val arb: Int,
    val major: Int,
    val minor: Int,
    val version: String,
    val status: String,
    val firstSeen: String,
    val lastChecked: String,
    val md5: String,
    val regions: List<String>
)

data class DeviceMatchResult(
    val model: String,
    val deviceName: String,
    val versions: List<VersionInfo>
)

object ArbDatabaseParser {
    private fun getDeviceModel(): String = Build.MODEL
    
    private fun getBuildNumber(): String = Build.ID
    
    fun matchDeviceInDatabase(database: Map<String, ArbDatabase>): DeviceMatchResult? {
        val model = getDeviceModel()
        
        database.forEach { (key, value) ->
            if (key.equals(model, ignoreCase = true)) {
                return DeviceMatchResult(
                    model = key,
                    deviceName = value.deviceName,
                    versions = value.versions.values.toList()
                )
            }
        }
        
        database.forEach { (key, value) ->
            if (model.contains(key, ignoreCase = true) || key.contains(model, ignoreCase = true)) {
                return DeviceMatchResult(
                    model = key,
                    deviceName = value.deviceName,
                    versions = value.versions.values.toList()
                )
            }
        }
        
        return null
    }
}
