package com.lanshare.explorer.ui

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lanshare.explorer.R
import com.lanshare.explorer.model.SmbFileItem
import com.lanshare.explorer.model.SmbShare
import com.lanshare.explorer.service.FileTransferService
import com.lanshare.explorer.smb.SmbClientManager
import com.lanshare.explorer.util.SettingsManager
import kotlinx.coroutines.launch

/**
 * 共享浏览界面 - 显示共享列表和文件浏览
 */
class ShareBrowserActivity : AppCompatActivity() {

    private lateinit var smbClient: SmbClientManager
    private lateinit var settings: SettingsManager
    private lateinit var fileAdapter: FileAdapter
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: LinearLayout

    private var serverIp: String = ""
    private var username: String = "guest"
    private var password: String = ""
    private var currentShare: String = ""
    private var currentPath: String = ""
    private val pathStack = mutableListOf<String>()

    // 浏览模式：共享列表 or 文件列表
    private var browseMode = BrowseMode.SHARES
    private val shares = mutableListOf<SmbShare>()
    private val files = mutableListOf<SmbFileItem>()

    enum class BrowseMode {
        SHARES, FILES
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_share_browser)

        // 获取参数
        serverIp = intent.getStringExtra("serverIp") ?: ""
        username = intent.getStringExtra("username") ?: "guest"
        password = intent.getStringExtra("password") ?: ""

        // 初始化工具栏
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.title = serverIp
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // 初始化
        smbClient = SmbClientManager()
        settings = SettingsManager(this)

        swipeRefresh = findViewById(R.id.swipeRefresh)
        recyclerView = findViewById(R.id.recyclerView)
        emptyView = findViewById(R.id.emptyView)

        // 设置RecyclerView
        fileAdapter = FileAdapter(mutableListOf(), 
            onItemClick = { item -> onFileClick(item) },
            onItemLongClick = { item -> onFileLongClick(item) }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = fileAdapter

        swipeRefresh.setOnRefreshListener {
            refreshCurrentDir()
        }

        // 连接并加载
        connectAndLoad()
    }

    private fun connectAndLoad() {
        swipeRefresh.isRefreshing = true
        lifecycleScope.launch {
            val connected = smbClient.connect(serverIp, username, password)
            if (connected) {
                loadShares()
            } else {
                runOnUiThread {
                    swipeRefresh.isRefreshing = false
                    showLoginDialog()
                }
            }
        }
    }

    private suspend fun loadShares() {
        val shareList = smbClient.listShares(serverIp)
        runOnUiThread {
            swipeRefresh.isRefreshing = false
            shares.clear()
            shares.addAll(shareList)
            browseMode = BrowseMode.SHARES
            currentShare = ""
            currentPath = ""
            pathStack.clear()
            supportActionBar?.subtitle = "共享列表"
            updateFileList()
        }
    }

    private suspend fun loadFiles(shareName: String, path: String) {
        val fileList = smbClient.listFiles(serverIp, shareName, path)
        runOnUiThread {
            swipeRefresh.isRefreshing = false
            files.clear()
            files.addAll(fileList)
            browseMode = BrowseMode.FILES
            currentShare = shareName
            currentPath = path
            supportActionBar?.subtitle = "$shareName/${path.replace("\\", "/")}"
            updateFileList()
        }
    }

    private fun updateFileList() {
        val items = when (browseMode) {
            BrowseMode.SHARES -> shares.map { share ->
                SmbFileItem(
                    name = share.displayName,
                    path = share.name,
                    isDirectory = true,
                    size = 0
                )
            }
            BrowseMode.FILES -> files
        }

        fileAdapter.updateItems(items)
        emptyView.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        recyclerView.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun onFileClick(item: SmbFileItem) {
        if (item.isDirectory) {
            when (browseMode) {
                BrowseMode.SHARES -> {
                    // 点击共享文件夹，进入文件浏览
                    pathStack.clear()
                    pathStack.add("")
                    lifecycleScope.launch {
                        swipeRefresh.isRefreshing = true
                        loadFiles(item.path, "")
                    }
                }
                BrowseMode.FILES -> {
                    // 进入子目录
                    val newPath = if (currentPath.isEmpty()) item.name else "$currentPath\\${item.name}"
                    pathStack.add(currentPath)
                    lifecycleScope.launch {
                        swipeRefresh.isRefreshing = true
                        loadFiles(currentShare, newPath)
                    }
                }
            }
        } else {
            // 文件点击 - 显示操作菜单
            showFileOptions(item)
        }
    }

    private fun onFileLongClick(item: SmbFileItem): Boolean {
        showFileOptions(item)
        return true
    }

    private fun showFileOptions(item: SmbFileItem) {
        val options = mutableListOf<String>().apply {
            if (!item.isDirectory) {
                add("下载到本地")
                add("查看详情")
            }
            add("重命名")
            add("删除")
            if (item.isDirectory) {
                add("新建文件夹")
            }
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(item.name)
            .setItems(options.toTypedArray()) { _, which ->
                when (options[which]) {
                    "下载到本地" -> downloadFile(item)
                    "查看详情" -> showFileDetails(item)
                    "重命名" -> showRenameDialog(item)
                    "删除" -> confirmDelete(item)
                    "新建文件夹" -> showNewFolderDialog()
                }
            }
            .show()
    }

    private fun downloadFile(item: SmbFileItem) {
        val localDir = java.io.File(settings.downloadPath)
        if (!localDir.exists()) localDir.mkdirs()
        val localPath = "${localDir.absolutePath}/${item.name}"

        FileTransferService.startDownload(
            this, serverIp, currentShare, item.path, localPath
        )
        Toast.makeText(this, "开始下载: ${item.name}", Toast.LENGTH_SHORT).show()
    }

    private fun showFileDetails(item: SmbFileItem) {
        val details = """
            文件名: ${item.name}
            路径: ${item.path}
            大小: ${item.displaySize}
            类型: ${if (item.isDirectory) "文件夹" else "文件"}
            修改时间: ${com.lanshare.explorer.util.Utils.formatTimestamp(item.lastModified)}
        """.trimIndent()

        MaterialAlertDialogBuilder(this)
            .setTitle("文件详情")
            .setMessage(details)
            .setPositiveButton("确定", null)
            .show()
    }

    private fun showRenameDialog(item: SmbFileItem) {
        val input = com.google.android.material.textfield.TextInputEditText(this).apply {
            setText(item.name)
            setSelection(item.name.length)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("重命名")
            .setView(input)
            .setPositiveButton("确定") { _, _ ->
                val newName = input.text?.toString()?.trim() ?: ""
                if (newName.isNotBlank() && newName != item.name) {
                    val newPath = if (currentPath.isEmpty()) newName else "$currentPath\\$newName"
                    lifecycleScope.launch {
                        val success = smbClient.rename(serverIp, currentShare, item.path, newPath)
                        runOnUiThread {
                            if (success) {
                                Toast.makeText(this@ShareBrowserActivity, "重命名成功", Toast.LENGTH_SHORT).show()
                                refreshCurrentDir()
                            } else {
                                Toast.makeText(this@ShareBrowserActivity, "重命名失败", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun confirmDelete(item: SmbFileItem) {
        MaterialAlertDialogBuilder(this)
            .setTitle("确认删除")
            .setMessage("确定要删除 \"${item.name}\" 吗？此操作不可撤销。")
            .setPositiveButton("删除") { _, _ ->
                lifecycleScope.launch {
                    val success = if (item.isDirectory) {
                        smbClient.deleteDirectory(serverIp, currentShare, item.path)
                    } else {
                        smbClient.deleteFile(serverIp, currentShare, item.path)
                    }
                    runOnUiThread {
                        if (success) {
                            Toast.makeText(this@ShareBrowserActivity, "删除成功", Toast.LENGTH_SHORT).show()
                            refreshCurrentDir()
                        } else {
                            Toast.makeText(this@ShareBrowserActivity, "删除失败", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showNewFolderDialog() {
        val input = com.google.android.material.textfield.TextInputEditText(this)

        MaterialAlertDialogBuilder(this)
            .setTitle("新建文件夹")
            .setView(input)
            .setPositiveButton("创建") { _, _ ->
                val folderName = input.text?.toString()?.trim() ?: ""
                if (folderName.isNotBlank()) {
                    val newPath = if (currentPath.isEmpty()) folderName else "$currentPath\\$folderName"
                    lifecycleScope.launch {
                        val success = smbClient.createDirectory(serverIp, currentShare, newPath)
                        runOnUiThread {
                            if (success) {
                                Toast.makeText(this@ShareBrowserActivity, "文件夹创建成功", Toast.LENGTH_SHORT).show()
                                refreshCurrentDir()
                            } else {
                                Toast.makeText(this@ShareBrowserActivity, "文件夹创建失败", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showLoginDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_login, null)
        val etUsername = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etUsername)
        val etPassword = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etPassword)

        MaterialAlertDialogBuilder(this)
            .setTitle("需要认证")
            .setMessage("匿名访问被拒绝，请输入用户名和密码")
            .setView(dialogView)
            .setPositiveButton("连接") { _, _ ->
                username = etUsername.text?.toString() ?: "guest"
                password = etPassword.text?.toString() ?: ""
                connectAndLoad()
            }
            .setNegativeButton("返回") { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun refreshCurrentDir() {
        swipeRefresh.isRefreshing = true
        lifecycleScope.launch {
            when (browseMode) {
                BrowseMode.SHARES -> loadShares()
                BrowseMode.FILES -> loadFiles(currentShare, currentPath)
            }
        }
    }

    override fun onBackPressed() {
        if (browseMode == BrowseMode.FILES && pathStack.isNotEmpty()) {
            val parentPath = pathStack.removeAt(pathStack.lastIndex)
            if (parentPath.isEmpty() && pathStack.isEmpty()) {
                // 回到共享列表
                lifecycleScope.launch {
                    loadShares()
                }
            } else {
                lifecycleScope.launch {
                    swipeRefresh.isRefreshing = true
                    loadFiles(currentShare, parentPath)
                }
            }
        } else {
            super.onBackPressed()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_browser, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_refresh -> {
                refreshCurrentDir()
                true
            }
            R.id.action_new_folder -> {
                showNewFolderDialog()
                true
            }
            R.id.action_disconnect -> {
                smbClient.disconnect(serverIp)
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        smbClient.destroy()
    }
}
