package com.lanshare.explorer.model

/**
 * 文件操作类型
 */
enum class FileOperation {
    COPY, MOVE, DELETE, RENAME, DOWNLOAD, UPLOAD
}

/**
 * 文件操作结果
 */
data class FileOperationResult(
    val operation: FileOperation,
    val success: Boolean,
    val sourcePath: String,
    val destPath: String = "",
    val errorMessage: String = ""
)
