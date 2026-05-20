package com.lanshare.explorer.util

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager

/**
 * 应用设置管理
 */
class SettingsManager(context: Context) {

    private val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    companion object {
        private const val KEY_SMB_USERNAME = "smb_username"
        private const val KEY_SMB_PASSWORD = "smb_password"
        private const val KEY_SCAN_TIMEOUT = "scan_timeout"
        private const val KEY_DOWNLOAD_PATH = "download_path"
        private const val KEY_SHOW_HIDDEN_FILES = "show_hidden_files"
        private const val KEY_AUTO_GUEST_CONNECT = "auto_guest_connect"
        private const val KEY_RECENT_SERVERS = "recent_servers"
    }

    // SMB用户名
    var smbUsername: String
        get() = prefs.getString(KEY_SMB_USERNAME, "guest") ?: "guest"
        set(value) = prefs.edit().putString(KEY_SMB_USERNAME, value).apply()

    // SMB密码
    var smbPassword: String
        get() = prefs.getString(KEY_SMB_PASSWORD, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SMB_PASSWORD, value).apply()

    // 扫描超时（毫秒）
    var scanTimeout: Int
        get() = prefs.getString(KEY_SCAN_TIMEOUT, "1000")?.toIntOrNull() ?: 1000
        set(value) = prefs.edit().putString(KEY_SCAN_TIMEOUT, value.toString()).apply()

    // 默认下载路径
    var downloadPath: String
        get() = prefs.getString(
            KEY_DOWNLOAD_PATH,
            android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_DOWNLOADS
            ).absolutePath + "/LanShare"
        ) ?: ""
        set(value) = prefs.edit().putString(KEY_DOWNLOAD_PATH, value).apply()

    // 是否显示隐藏文件
    var showHiddenFiles: Boolean
        get() = prefs.getBoolean(KEY_SHOW_HIDDEN_FILES, false)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_HIDDEN_FILES, value).apply()

    // 是否自动连接匿名共享
    var autoGuestConnect: Boolean
        get() = prefs.getBoolean(KEY_AUTO_GUEST_CONNECT, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_GUEST_CONNECT, value).apply()

    // 记住最近连接的服务器
    var recentServers: Set<String>
        get() = prefs.getStringSet(KEY_RECENT_SERVERS, emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_RECENT_SERVERS, value).apply()

    fun addRecentServer(ip: String) {
        val current = LinkedHashSet(recentServers)
        current.remove(ip)
        current.add(ip)
        while (current.size > 10) {
            current.remove(current.first())
        }
        recentServers = current
    }

    fun removeRecentServer(ip: String) {
        val current = recentServers.toMutableSet()
        current.remove(ip)
        recentServers = current
    }
}
