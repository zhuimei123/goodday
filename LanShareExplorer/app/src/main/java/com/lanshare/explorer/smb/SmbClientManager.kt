package com.lanshare.explorer.smb

import android.util.Log
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.msfscc.FileNotifyAction
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2CreateOptions
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.mssmb2.SMBApiException
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.msfscc.fileinformation.FileRenameInformation
import com.hierynomus.smbj.share.DiskShare
import com.hierynomus.smbj.share.File
import com.lanshare.explorer.model.SmbFileItem
import com.lanshare.explorer.model.SmbShare
import com.lanshare.explorer.model.ShareType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.*

/**
 * SMB客户端管理器
 * 
 * 封装SMBJ库，提供共享浏览和文件操作能力
 */
class SmbClientManager {

    companion object {
        private const val TAG = "SmbClientManager"
        private const val BUFFER_SIZE = 8192
    }

    private val client = SMBClient()
    private val connections = mutableMapOf<String, Connection>()
    private val sessions = mutableMapOf<String, Session>()

    /**
     * 连接到SMB服务器
     */
    suspend fun connect(
        serverIp: String,
        username: String = "guest",
        password: String = ""
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // 关闭已有连接
            disconnect(serverIp)

            val connection = client.connect(serverIp)
            connections[serverIp] = connection

            val authContext = if (username == "guest") {
                AuthenticationContext.guest()
            } else {
                AuthenticationContext(username, password.toCharArray(), null)
            }

            val session = connection.authenticate(authContext)
            sessions[serverIp] = session

            Log.i(TAG, "SMB连接成功: $serverIp")
            true
        } catch (e: Exception) {
            Log.e(TAG, "SMB连接失败: $serverIp", e)
            false
        }
    }

    /**
     * 列出共享目录
     */
    suspend fun listShares(serverIp: String): List<SmbShare> = withContext(Dispatchers.IO) {
        val session = sessions[serverIp]
        if (session == null) {
            Log.e(TAG, "未连接到服务器: $serverIp")
            return@withContext emptyList()
        }

        try {
            // SMBJ 0.12.2 不直接支持列出服务器共享名，因此此处返回空列表以保持兼容性。
            Log.w(TAG, "SMB share enumeration is not supported by SMBJ 0.12.2")
            emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "列出共享失败: $serverIp", e)
            emptyList()
        }
    }

    /**
     * 列出共享目录下的文件和文件夹
     */
    suspend fun listFiles(
        serverIp: String,
        shareName: String,
        path: String = ""
    ): List<SmbFileItem> = withContext(Dispatchers.IO) {
        val session = sessions[serverIp]
        if (session == null) {
            Log.e(TAG, "未连接到服务器: $serverIp")
            return@withContext emptyList()
        }

        try {
            val share = session.connectShare(shareName) as? DiskShare
                ?: return@withContext emptyList()

            val folderPath = if (path.isEmpty()) "" else path
            val items = mutableListOf<SmbFileItem>()

            val directories = share.list(folderPath)
            for (item in directories) {
                val itemName = item.fileName
                if (itemName == "." || itemName == "..") continue

                val attributes = item.fileAttributes
                val isDirectory = (attributes and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value) != 0L
                val fullPath = if (folderPath.isEmpty()) itemName else "$folderPath\\$itemName"

                val fileItem = SmbFileItem(
                    name = itemName,
                    path = fullPath,
                    isDirectory = isDirectory,
                    size = if (!isDirectory) item.endOfFile else 0L,
                    lastModified = item.lastWriteTime?.toEpochMillis() ?: 0L
                )
                items.add(fileItem)
            }

            // 排序：文件夹在前，然后按名称排序
            items.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
        } catch (e: SMBApiException) {
            Log.e(TAG, "列出文件失败: $shareName/$path, code=${e.statusCode}", e)
            emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "列出文件失败: $shareName/$path", e)
            emptyList()
        }
    }

    /**
     * 下载文件到本地
     */
    suspend fun downloadFile(
        serverIp: String,
        shareName: String,
        remotePath: String,
        localPath: String,
        onProgress: ((Long, Long) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val session = sessions[serverIp] ?: return@withContext false

        try {
            val share = session.connectShare(shareName) as? DiskShare
                ?: return@withContext false

            val remoteFile = share.openFile(
                remotePath,
                setOf(AccessMask.FILE_READ_DATA),
                null,
                setOf(SMB2ShareAccess.FILE_SHARE_READ),
                SMB2CreateDisposition.FILE_OPEN,
                setOf(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE)
            )

            val inputStream = remoteFile.inputStream
            val outputStream = FileOutputStream(localPath)

            copyStream(inputStream, outputStream) { transferred, total ->
                onProgress?.invoke(transferred, total)
            }

            remoteFile.close()
            Log.i(TAG, "文件下载成功: $remotePath -> $localPath")
            true
        } catch (e: Exception) {
            Log.e(TAG, "文件下载失败: $remotePath", e)
            false
        }
    }

    /**
     * 上传本地文件到共享
     */
    suspend fun uploadFile(
        serverIp: String,
        shareName: String,
        localPath: String,
        remotePath: String,
        onProgress: ((Long, Long) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val session = sessions[serverIp] ?: return@withContext false

        try {
            val share = session.connectShare(shareName) as? DiskShare
                ?: return@withContext false

            val remoteFile = share.openFile(
                remotePath,
                setOf(AccessMask.FILE_WRITE_DATA, AccessMask.FILE_READ_ATTRIBUTES),
                null,
                setOf(SMB2ShareAccess.FILE_SHARE_WRITE),
                SMB2CreateDisposition.FILE_OVERWRITE_IF,
                setOf(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE)
            )

            val inputStream = java.io.FileInputStream(localPath)
            val outputStream = remoteFile.outputStream
            val totalSize = java.io.File(localPath).length()

            copyStream(inputStream, outputStream, totalSize) { transferred, total ->
                onProgress?.invoke(transferred, total)
            }

            remoteFile.close()
            Log.i(TAG, "文件上传成功: $localPath -> $remotePath")
            true
        } catch (e: Exception) {
            Log.e(TAG, "文件上传失败: $localPath -> $remotePath", e)
            false
        }
    }

    /**
     * 删除文件
     */
    suspend fun deleteFile(
        serverIp: String,
        shareName: String,
        remotePath: String
    ): Boolean = withContext(Dispatchers.IO) {
        val session = sessions[serverIp] ?: return@withContext false

        try {
            val share = session.connectShare(shareName) as? DiskShare
                ?: return@withContext false

            share.rm(remotePath)
            Log.i(TAG, "文件删除成功: $remotePath")
            true
        } catch (e: Exception) {
            Log.e(TAG, "文件删除失败: $remotePath", e)
            false
        }
    }

    /**
     * 删除文件夹
     */
    suspend fun deleteDirectory(
        serverIp: String,
        shareName: String,
        remotePath: String
    ): Boolean = withContext(Dispatchers.IO) {
        val session = sessions[serverIp] ?: return@withContext false

        try {
            val share = session.connectShare(shareName) as? DiskShare
                ?: return@withContext false

            share.rmdir(remotePath, true)
            Log.i(TAG, "文件夹删除成功: $remotePath")
            true
        } catch (e: Exception) {
            Log.e(TAG, "文件夹删除失败: $remotePath", e)
            false
        }
    }

    /**
     * 创建文件夹
     */
    suspend fun createDirectory(
        serverIp: String,
        shareName: String,
        remotePath: String
    ): Boolean = withContext(Dispatchers.IO) {
        val session = sessions[serverIp] ?: return@withContext false

        try {
            val share = session.connectShare(shareName) as? DiskShare
                ?: return@withContext false

            share.mkdir(remotePath)
            Log.i(TAG, "文件夹创建成功: $remotePath")
            true
        } catch (e: Exception) {
            Log.e(TAG, "文件夹创建失败: $remotePath", e)
            false
        }
    }

    /**
     * 重命名文件/文件夹
     */
    suspend fun rename(
        serverIp: String,
        shareName: String,
        oldPath: String,
        newPath: String
    ): Boolean = withContext(Dispatchers.IO) {
        val session = sessions[serverIp] ?: return@withContext false

        try {
            val share = session.connectShare(shareName) as? DiskShare
                ?: return@withContext false

            val renameInfo = FileRenameInformation(true, 0L, newPath)
            share.setFileInformation(oldPath, renameInfo)
            Log.i(TAG, "重命名成功: $oldPath -> $newPath")
            true
        } catch (e: Exception) {
            Log.e(TAG, "重命名失败: $oldPath -> $newPath", e)
            false
        }
    }

    /**
     * 复制流数据
     */
    private fun copyStream(
        input: InputStream,
        output: OutputStream,
        totalSize: Long = -1,
        onProgress: ((Long, Long) -> Unit)? = null
    ) {
        val buffer = ByteArray(BUFFER_SIZE)
        var totalRead = 0L

        input.use { inputStream ->
            output.use { outputStream ->
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalRead += bytesRead
                    if (totalSize > 0) {
                        onProgress?.invoke(totalRead, totalSize)
                    }
                }
                outputStream.flush()
            }
        }
    }

    /**
     * 断开指定服务器连接
     */
    fun disconnect(serverIp: String) {
        sessions[serverIp]?.close()
        sessions.remove(serverIp)
        connections[serverIp]?.close()
        connections.remove(serverIp)
    }

    /**
     * 断开所有连接
     */
    fun disconnectAll() {
        sessions.values.forEach { it.close() }
        sessions.clear()
        connections.values.forEach { it.close() }
        connections.clear()
    }

    /**
     * 销毁客户端
     */
    fun destroy() {
        disconnectAll()
        client.close()
    }
}
