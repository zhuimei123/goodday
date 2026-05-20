package com.lanshare.explorer.model

/**
 * SMB共享信息
 */
data class SmbShare(
    val name: String,
    val serverIp: String,
    val comment: String = "",
    val type: ShareType = ShareType.DISK
) {
    val path: String
        get() = "smb://$serverIp/$name/"

    val displayName: String
        get() = if (comment.isNotBlank()) "$name ($comment)" else name
}

enum class ShareType {
    DISK,       // 磁盘共享
    PRINTER,    // 打印机
    IPC         // IPC$ 管道
}
