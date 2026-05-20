package com.lanshare.explorer.model

/**
 * SMB文件/文件夹信息
 */
data class SmbFileItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long = 0,
    val lastModified: Long = 0,
    val isReadable: Boolean = true,
    val isWritable: Boolean = false
) {
    val fileExtension: String
        get() = if (name.contains(".")) name.substringAfterLast(".") else ""

    val displaySize: String
        get() = formatFileSize(size)

    companion object {
        fun formatFileSize(size: Long): String {
            return when {
                size < 1024 -> "$size B"
                size < 1024 * 1024 -> String.format("%.1f KB", size / 1024.0)
                size < 1024 * 1024 * 1024 -> String.format("%.1f MB", size / (1024.0 * 1024))
                else -> String.format("%.1f GB", size / (1024.0 * 1024 * 1024))
            }
        }
    }
}
