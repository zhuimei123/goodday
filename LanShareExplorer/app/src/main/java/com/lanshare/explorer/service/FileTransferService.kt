package com.lanshare.explorer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.lanshare.explorer.model.FileOperation
import com.lanshare.explorer.model.FileOperationResult
import com.lanshare.explorer.smb.SmbClientManager
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * 文件操作后台服务
 * 
 * 负责在后台执行文件的上传、下载、复制、删除等耗时操作
 * 使用前台通知显示操作进度
 */
class FileTransferService : Service() {

    companion object {
        private const val TAG = "FileTransferService"
        private const val CHANNEL_ID = "file_transfer_channel"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_DOWNLOAD = "action_download"
        const val ACTION_UPLOAD = "action_upload"
        const val ACTION_DELETE = "action_delete"
        const val ACTION_CANCEL = "action_cancel"

        const val EXTRA_SERVER_IP = "extra_server_ip"
        const val EXTRA_SHARE_NAME = "extra_share_name"
        const val EXTRA_REMOTE_PATH = "extra_remote_path"
        const val EXTRA_LOCAL_PATH = "extra_local_path"

        fun startDownload(context: Context, serverIp: String, shareName: String, remotePath: String, localPath: String) {
            val intent = Intent(context, FileTransferService::class.java).apply {
                action = ACTION_DOWNLOAD
                putExtra(EXTRA_SERVER_IP, serverIp)
                putExtra(EXTRA_SHARE_NAME, shareName)
                putExtra(EXTRA_REMOTE_PATH, remotePath)
                putExtra(EXTRA_LOCAL_PATH, localPath)
            }
            context.startForegroundService(intent)
        }

        fun startUpload(context: Context, serverIp: String, shareName: String, remotePath: String, localPath: String) {
            val intent = Intent(context, FileTransferService::class.java).apply {
                action = ACTION_UPLOAD
                putExtra(EXTRA_SERVER_IP, serverIp)
                putExtra(EXTRA_SHARE_NAME, shareName)
                putExtra(EXTRA_REMOTE_PATH, remotePath)
                putExtra(EXTRA_LOCAL_PATH, localPath)
            }
            context.startForegroundService(intent)
        }

        fun startDelete(context: Context, serverIp: String, shareName: String, remotePath: String) {
            val intent = Intent(context, FileTransferService::class.java).apply {
                action = ACTION_DELETE
                putExtra(EXTRA_SERVER_IP, serverIp)
                putExtra(EXTRA_SHARE_NAME, shareName)
                putExtra(EXTRA_REMOTE_PATH, remotePath)
            }
            context.startForegroundService(intent)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val smbClient = SmbClientManager()
    private val operationQueue = ConcurrentLinkedQueue<PendingOperation>()
    private var isProcessing = false
    private var currentJob: Job? = null

    data class PendingOperation(
        val action: String,
        val serverIp: String,
        val shareName: String,
        val remotePath: String,
        val localPath: String = ""
    )

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("准备中..."))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                currentJob?.cancel()
                stopSelf()
            }
            else -> {
                val operation = PendingOperation(
                    action = intent?.action ?: "",
                    serverIp = intent?.getStringExtra(EXTRA_SERVER_IP) ?: "",
                    shareName = intent?.getStringExtra(EXTRA_SHARE_NAME) ?: "",
                    remotePath = intent?.getStringExtra(EXTRA_REMOTE_PATH) ?: "",
                    localPath = intent?.getStringExtra(EXTRA_LOCAL_PATH) ?: ""
                )
                operationQueue.add(operation)
                processQueue()
            }
        }
        return START_NOT_STICKY
    }

    private fun processQueue() {
        if (isProcessing) return
        isProcessing = true

        val operation = operationQueue.poll() ?: run {
            isProcessing = false
            stopSelf()
            return
        }

        currentJob = serviceScope.launch {
            executeOperation(operation)
            isProcessing = false
            processQueue()
        }
    }

    private suspend fun executeOperation(op: PendingOperation) {
        updateNotification("正在处理: ${op.remotePath.substringAfterLast("\\")}")

        when (op.action) {
            ACTION_DOWNLOAD -> {
                val success = smbClient.downloadFile(
                    op.serverIp, op.shareName, op.remotePath, op.localPath
                ) { transferred, total ->
                    val percent = if (total > 0) (transferred * 100 / total).toInt() else 0
                    updateNotification("下载中: ${percent}%")
                }
                broadcastResult(FileOperationResult(
                    FileOperation.DOWNLOAD, success, op.remotePath, op.localPath
                ))
            }
            ACTION_UPLOAD -> {
                val success = smbClient.uploadFile(
                    op.serverIp, op.shareName, op.localPath, op.remotePath
                ) { transferred, total ->
                    val percent = if (total > 0) (transferred * 100 / total).toInt() else 0
                    updateNotification("上传中: ${percent}%")
                }
                broadcastResult(FileOperationResult(
                    FileOperation.UPLOAD, success, op.localPath, op.remotePath
                ))
            }
            ACTION_DELETE -> {
                val success = smbClient.deleteFile(
                    op.serverIp, op.shareName, op.remotePath
                )
                broadcastResult(FileOperationResult(
                    FileOperation.DELETE, success, op.remotePath
                ))
            }
        }
    }

    private fun broadcastResult(result: FileOperationResult) {
        val intent = Intent("com.lanshare.explorer.FILE_OPERATION_RESULT").apply {
            putExtra("operation", result.operation.name)
            putExtra("success", result.success)
            putExtra("sourcePath", result.sourcePath)
            putExtra("destPath", result.destPath)
            putExtra("errorMessage", result.errorMessage)
        }
        sendBroadcast(intent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "文件传输",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "显示文件传输进度"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("局域网共享")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_upload)
            .setProgress(100, 0, true)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val notification = buildNotification(text)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        smbClient.destroy()
    }
}
