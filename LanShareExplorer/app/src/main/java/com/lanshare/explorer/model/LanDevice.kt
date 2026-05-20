package com.lanshare.explorer.model

/**
 * 局域网设备信息
 */
data class LanDevice(
    val ipAddress: String,
    val hostName: String = "",
    val macAddress: String = "",
    val shares: List<SmbShare> = emptyList(),
    val isOnline: Boolean = true,
    val responseTime: Long = 0
) {
    val displayName: String
        get() = if (hostName.isNotBlank()) "$hostName ($ipAddress)" else ipAddress
}
