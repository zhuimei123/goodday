package com.lanshare.explorer.ui

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.lanshare.explorer.R
import com.lanshare.explorer.model.LanDevice
import com.lanshare.explorer.scanner.LanScanner
import com.lanshare.explorer.util.SettingsManager
import kotlinx.coroutines.launch

/**
 * 主界面 - 扫描局域网设备
 */
class MainActivity : AppCompatActivity(), LanScanner.ScanCallback {

    private lateinit var scanner: LanScanner
    private lateinit var settings: SettingsManager
    private lateinit var deviceAdapter: DeviceAdapter
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: LinearLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var tvProgress: TextView
    private lateinit var fabScan: FloatingActionButton

    private val devices = mutableListOf<LanDevice>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 初始化工具栏
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.title = "局域网共享扫描"

        // 初始化组件
        scanner = LanScanner()
        settings = SettingsManager(this)

        // 绑定视图
        swipeRefresh = findViewById(R.id.swipeRefresh)
        recyclerView = findViewById(R.id.recyclerView)
        emptyView = findViewById(R.id.emptyView)
        progressBar = findViewById(R.id.progressBar)
        tvProgress = findViewById(R.id.tvProgress)
        fabScan = findViewById(R.id.fabScan)

        // 设置RecyclerView
        deviceAdapter = DeviceAdapter(devices) { device ->
            onDeviceClick(device)
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = deviceAdapter

        // 下拉刷新
        swipeRefresh.setOnRefreshListener {
            startScan()
        }

        // 扫描按钮
        fabScan.setOnClickListener {
            startScan()
        }

        // 自动扫描
        startScan()
    }

    private fun startScan() {
        if (swipeRefresh.isRefreshing) return

        devices.clear()
        deviceAdapter.notifyDataSetChanged()
        showEmptyState(true, "正在扫描局域网...")

        swipeRefresh.isRefreshing = true
        fabScan.isEnabled = false
        scanner.startScan(this)
    }

    private fun onDeviceClick(device: LanDevice) {
        // 询问SMB凭据
        if (settings.autoGuestConnect) {
            navigateToShares(device.ipAddress, "guest", "")
        } else {
            showLoginDialog(device)
        }
        settings.addRecentServer(device.ipAddress)
    }

    private fun showLoginDialog(device: LanDevice) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_login, null)
        val etUsername = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etUsername)
        val etPassword = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etPassword)

        etUsername.setText(settings.smbUsername)

        MaterialAlertDialogBuilder(this)
            .setTitle("连接到 ${device.displayName}")
            .setView(dialogView)
            .setPositiveButton("连接") { _, _ ->
                val username = etUsername.text?.toString() ?: "guest"
                val password = etPassword.text?.toString() ?: ""
                navigateToShares(device.ipAddress, username, password)
            }
            .setNegativeButton("取消", null)
            .setNeutralButton("匿名访问") { _, _ ->
                navigateToShares(device.ipAddress, "guest", "")
            }
            .show()
    }

    private fun navigateToShares(serverIp: String, username: String, password: String) {
        val intent = Intent(this, ShareBrowserActivity::class.java).apply {
            putExtra("serverIp", serverIp)
            putExtra("username", username)
            putExtra("password", password)
        }
        startActivity(intent)
    }

    private fun showEmptyState(show: Boolean, message: String = "") {
        emptyView.visibility = if (show) View.VISIBLE else View.GONE
        recyclerView.visibility = if (show) View.GONE else View.VISIBLE
        if (message.isNotBlank()) {
            tvProgress.text = message
        }
    }

    // LanScanner.ScanCallback 实现
    override fun onDeviceFound(device: LanDevice) {
        runOnUiThread {
            if (devices.none { it.ipAddress == device.ipAddress }) {
                devices.add(device)
                deviceAdapter.notifyItemInserted(devices.size - 1)
                showEmptyState(false)
            }
        }
    }

    override fun onScanProgress(scanned: Int, total: Int) {
        runOnUiThread {
            val percent = (scanned * 100) / total
            progressBar.progress = percent
            tvProgress.text = "扫描中... $scanned/$total ($percent%)"
        }
    }

    override fun onScanComplete(foundDevices: List<LanDevice>) {
        runOnUiThread {
            swipeRefresh.isRefreshing = false
            fabScan.isEnabled = true
            progressBar.isIndeterminate = false
            progressBar.progress = 100

            if (devices.isEmpty()) {
                showEmptyState(true, "未发现共享设备\n请确保设备在同一局域网且已开启文件共享")
            } else {
                showEmptyState(false)
                Toast.makeText(this, "发现 ${devices.size} 台设备", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onScanError(error: String) {
        runOnUiThread {
            swipeRefresh.isRefreshing = false
            fabScan.isEnabled = true
            showEmptyState(true, "扫描出错: $error")
            Toast.makeText(this, error, Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            R.id.action_manual_connect -> {
                showManualConnectDialog()
                true
            }
            R.id.action_refresh -> {
                startScan()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showManualConnectDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_manual_connect, null)
        val etIp = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etServerIp)

        // 显示最近连接
        val recentServers = settings.recentServers
        if (recentServers.isNotEmpty()) {
            // 可以在dialog中添加最近服务器列表
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("手动连接")
            .setView(dialogView)
            .setPositiveButton("连接") { _, _ ->
                val ip = etIp.text?.toString()?.trim() ?: ""
                if (ip.isNotBlank()) {
                    navigateToShares(ip, settings.smbUsername, settings.smbPassword)
                } else {
                    Toast.makeText(this, "请输入IP地址", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        scanner.destroy()
    }
}
