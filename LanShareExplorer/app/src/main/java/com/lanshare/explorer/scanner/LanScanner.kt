package com.lanshare.explorer.scanner

import android.util.Log
import com.lanshare.explorer.model.LanDevice
import kotlinx.coroutines.*
import java.net.*
import java.util.concurrent.ConcurrentHashMap

/**
 * 局域网设备扫描器
 * 
 * 扫描策略：
 * 1. 获取本机IP和子网信息
 * 2. 并发ping扫描整个子网
 * 3. 对响应的IP尝试SMB连接（445端口）
 * 4. 尝试解析主机名
 */
class LanScanner {

    companion object {
        private const val TAG = "LanScanner"
        private const val SMB_PORT = 445
        private const val PING_TIMEOUT = 500
        private const val CONNECT_TIMEOUT = 1000
        private const val SCAN_CONCURRENCY = 50
    }

    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var scanJob: Job? = null

    interface ScanCallback {
        fun onDeviceFound(device: LanDevice)
        fun onScanProgress(scanned: Int, total: Int)
        fun onScanComplete(devices: List<LanDevice>)
        fun onScanError(error: String)
    }

    /**
     * 获取本机IP地址和子网
     */
    fun getLocalNetworkInfo(): Pair<String, String>? {
        try {
            val socket = Socket()
            socket.connect(InetSocketAddress("8.8.8.8", 53), 1000)
            val localIp = socket.localAddress.hostAddress ?: return null
            socket.close()
            
            val subnet = getSubnetPrefix(localIp)
            return Pair(localIp, subnet)
        } catch (e: Exception) {
            Log.e(TAG, "获取本机网络信息失败", e)
            // 备用方案：遍历网络接口
            return getLocalNetworkFallback()
        }
    }

    private fun getSubnetPrefix(ip: String): String {
        val parts = ip.split(".")
        return "${parts[0]}.${parts[1]}.${parts[2]}"
    }

    private fun getLocalNetworkFallback(): Pair<String, String>? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            for (intf in interfaces) {
                if (intf.isLoopback || !intf.isUp) continue
                for (addr in intf.inetAddresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val ip = addr.hostAddress ?: continue
                        return Pair(ip, getSubnetPrefix(ip))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "备用获取网络信息失败", e)
        }
        return null
    }

    /**
     * 开始扫描局域网
     */
    fun startScan(callback: ScanCallback) {
        scanJob?.cancel()
        scanJob = coroutineScope.launch {
            val networkInfo = withContext(Dispatchers.Main) {
                getLocalNetworkInfo()
            }

            if (networkInfo == null) {
                withContext(Dispatchers.Main) {
                    callback.onScanError("无法获取本机网络信息，请检查WiFi连接")
                }
                return@launch
            }

            val (localIp, subnet) = networkInfo
            Log.i(TAG, "本机IP: $localIp, 子网: $subnet.x")

            val foundDevices = ConcurrentHashMap<String, LanDevice>()
            val totalHosts = 254
            var scannedCount = 0

            // 并发扫描子网内所有IP
            val scanJobs = (1..254).map { hostId ->
                async {
                    val targetIp = "$subnet.$hostId"
                    if (targetIp == localIp) {
                        scannedCount++
                        return@async
                    }

                    try {
                        // 先尝试SMB端口连接（更准确）
                        if (isSmbPortOpen(targetIp)) {
                            val hostName = resolveHostName(targetIp)
                            val device = LanDevice(
                                ipAddress = targetIp,
                                hostName = hostName,
                                isOnline = true
                            )
                            foundDevices[targetIp] = device
                            
                            withContext(Dispatchers.Main) {
                                callback.onDeviceFound(device)
                            }
                        }
                    } catch (e: Exception) {
                        // 忽略单个IP的扫描错误
                    }

                    scannedCount++
                    withContext(Dispatchers.Main) {
                        callback.onScanProgress(scannedCount, totalHosts)
                    }
                }
            }

            // 限制并发数
            scanJobs.chunked(SCAN_CONCURRENCY).forEach { chunk ->
                chunk.map { it.start() }
                chunk.awaitAll()
            }

            val deviceList = foundDevices.values.toList()
            withContext(Dispatchers.Main) {
                callback.onScanComplete(deviceList)
            }
        }
    }

    /**
     * 检测SMB端口是否开放
     */
    private fun isSmbPortOpen(ip: String): Boolean {
        return try {
            val socket = Socket()
            socket.connect(InetSocketAddress(ip, SMB_PORT), CONNECT_TIMEOUT)
            socket.close()
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 解析主机名
     */
    private fun resolveHostName(ip: String): String {
        return try {
            val addr = InetAddress.getByName(ip)
            val name = addr.hostName ?: ""
            if (name != ip) name else ""
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * 停止扫描
     */
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
