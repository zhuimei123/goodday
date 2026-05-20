package com.lanshare.explorer.scanner

import android.util.Log
import com.lanshare.explorer.model.LanDevice
import kotlinx.coroutines.*
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * 局域网设备扫描器
 */
class LanScanner {

    companion object {
        private const val TAG = "LanScanner"
        private const val SMB_PORT = 445
        private const val CONNECT_TIMEOUT = 800
        private const val SCAN_CONCURRENCY = 30
    }

    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var scanJob: Job? = null

    interface ScanCallback {
        fun onDeviceFound(device: LanDevice)
        fun onScanProgress(scanned: Int, total: Int)
        fun onScanComplete(devices: List<LanDevice>)
        fun onScanError(error: String)
    }

    private fun getLocalNetworkInfo(): Pair<String, String>? {
        try {
            val networkInterfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            for (intf in networkInterfaces) {
                if (!intf.isUp || intf.isLoopback) continue
                for (addr in intf.inetAddresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val ip = addr.hostAddress ?: continue
                        val subnet = getSubnetPrefix(ip) ?: continue
                        return Pair(ip, subnet)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取本机网络信息失败", e)
        }
        
        // 备用方案：尝试连接到8.8.8.8以获取本机IP
        return getLocalNetworkFallback()
    }

    private fun getLocalNetworkFallback(): Pair<String, String>? {
        return try {
            val socket = Socket()
            try {
                socket.connect(InetSocketAddress("8.8.8.8", 53), 1000)
                val localIp = socket.localAddress.hostAddress ?: return null
                val subnet = getSubnetPrefix(localIp)
                if (subnet != null) Pair(localIp, subnet) else null
            } finally {
                socket.close()
            }
        } catch (e: Exception) {
            Log.w(TAG, "备用获取网络信息方案失败", e)
            null
        }
    }

    private fun getSubnetPrefix(ip: String): String? {
        val parts = ip.split('.')
        if (parts.size != 4) return null
        return "${parts[0]}.${parts[1]}.${parts[2]}"
    }

    fun startScan(callback: ScanCallback) {
        scanJob?.cancel()
        scanJob = coroutineScope.launch {
            val networkInfo = getLocalNetworkInfo()
            if (networkInfo == null) {
                withContext(Dispatchers.Main) {
                    callback.onScanError("无法获取本机网络，请检查WiFi连接")
                }
                return@launch
            }

            val (localIp, subnet) = networkInfo
            Log.i(TAG, "本机IP: $localIp, 子网: $subnet.x")

            val totalHosts = 254
            val scanned = AtomicInteger(0)
            val foundDevices = ConcurrentHashMap<String, LanDevice>()

            val scanJobs = (1..254).map { hostId ->
                async {
                    val targetIp = "$subnet.$hostId"
                    if (targetIp == localIp) {
                        updateProgress(callback, scanned.incrementAndGet(), totalHosts)
                        return@async
                    }

                    try {
                        if (isSmbPortOpen(targetIp)) {
                            val hostName = resolveHostName(targetIp)
                            val device = LanDevice(
                                ipAddress = targetIp,
                                hostName = hostName,
                                isOnline = true
                            )
                            foundDevices[targetIp] = device
                            withContext(Dispatchers.Main) { callback.onDeviceFound(device) }
                        }
                    } catch (ignored: Exception) {
                    } finally {
                        updateProgress(callback, scanned.incrementAndGet(), totalHosts)
                    }
                }
            }

            // 分批执行，避免过多并发
            scanJobs.chunked(SCAN_CONCURRENCY).forEach { batch ->
                batch.awaitAll()
            }

            val deviceList = foundDevices.values.toList()
            withContext(Dispatchers.Main) {
                callback.onScanComplete(deviceList)
            }
        }
    }

    private suspend fun updateProgress(callback: ScanCallback, scanned: Int, total: Int) {
        withContext(Dispatchers.Main) {
            callback.onScanProgress(scanned, total)
        }
    }

    private fun isSmbPortOpen(ip: String): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, SMB_PORT), CONNECT_TIMEOUT)
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun resolveHostName(ip: String): String {
        return try {
            val address = InetAddress.getByName(ip)
            val hostName = address.hostName
            if (hostName != ip) hostName else ""
        } catch (e: Exception) {
            ""
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        Log.i(TAG, "扫描已停止")
    }

    fun destroy() {
        stopScan()
        coroutineScope.cancel()
    }
}
