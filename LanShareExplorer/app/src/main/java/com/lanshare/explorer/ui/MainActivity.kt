package com.lanshare.explorer.ui

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.lanshare.explorer.R
import com.lanshare.explorer.model.LanDevice
import com.lanshare.explorer.scanner.LanScanner

/**
 * 主界面 - 扫描局域网设备
 */
class MainActivity : AppCompatActivity(), LanScanner.ScanCallback {

    private lateinit var scanner: LanScanner
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

        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.title = "局域网设备扫描"

        scanner = LanScanner()

        swipeRefresh = findViewById(R.id.swipeRefresh)
        recyclerView = findViewById(R.id.recyclerView)
        emptyView = findViewById(R.id.emptyView)
        progressBar = findViewById(R.id.progressBar)
        tvProgress = findViewById(R.id.tvProgress)
        fabScan = findViewById(R.id.fabScan)

        deviceAdapter = DeviceAdapter(devices) { device -> showDeviceOptions(device) }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = deviceAdapter

        swipeRefresh.setOnRefreshListener { startScan() }
        fabScan.setOnClickListener { startScan() }

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

    private fun showDeviceOptions(device: LanDevice) {
        MaterialAlertDialogBuilder(this)
            .setTitle(device.hostName.ifBlank { "局域网设备" })
            .setMessage("IP: ${device.ipAddress}\n在线状态: ${if (device.isOnline) "在线" else "离线"}")
            .setPositiveButton("复制IP") { _, _ ->
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("IP地址", device.ipAddress)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "IP已复制", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun showEmptyState(show: Boolean, message: String = "") {
        emptyView.visibility = if (show) View.VISIBLE else View.GONE
        recyclerView.visibility = if (show) View.GONE else View.VISIBLE
        if (message.isNotBlank()) tvProgress.text = message
    }

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
            tvProgress.text = "扫描中 $scanned/$total ($percent%)"
        }
    }

    override fun onScanComplete(foundDevices: List<LanDevice>) {
        runOnUiThread {
            swipeRefresh.isRefreshing = false
            fabScan.isEnabled = true
            progressBar.isIndeterminate = false
            progressBar.progress = 100
            if (foundDevices.isEmpty()) {
                showEmptyState(true, "未发现设备，请检查WiFi连接并确保设备在同一局域网")
            } else {
                showEmptyState(false)
                Toast.makeText(this, "已发现 ${foundDevices.size} 台设备", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onScanError(error: String) {
        runOnUiThread {
            swipeRefresh.isRefreshing = false
            fabScan.isEnabled = true
            showEmptyState(true, "扫描失败: $error")
            Toast.makeText(this, error, Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_refresh -> {
                startScan()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scanner.destroy()
    }
}
