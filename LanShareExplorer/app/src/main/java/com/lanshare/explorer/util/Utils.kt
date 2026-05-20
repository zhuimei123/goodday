package com.lanshare.explorer.util

import java.text.SimpleDateFormat
import java.util.*

/**
 * 通用工具类
 */
object Utils {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    /**
     * 格式化时间戳
     */
    fun formatTimestamp(timestamp: Long): String {
        return if (timestamp > 0) {
            dateFormat.format(Date(timestamp))
        } else {
            ""
        }
    }

    /**
     * 根据文件扩展名获取文件类型图标标识
     */
    fun getFileTypeIcon(fileName: String): Int {
        val ext = fileName.substringAfterLast(".", "").lowercase()
        return when (ext) {
            in setOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "svg") -> FileType.IMAGE
            in setOf("mp4", "avi", "mkv", "mov", "wmv", "flv") -> FileType.VIDEO
            in setOf("mp3", "wav", "flac", "aac", "ogg", "wma") -> FileType.AUDIO
            in setOf("pdf") -> FileType.PDF
            in setOf("doc", "docx") -> FileType.WORD
            in setOf("xls", "xlsx") -> FileType.EXCEL
            in setOf("ppt", "pptx") -> FileType.PPT
            in setOf("txt", "log", "md", "csv") -> FileType.TEXT
            in setOf("zip", "rar", "7z", "tar", "gz") -> FileType.ARCHIVE
            in setOf("apk") -> FileType.APK
            else -> FileType.OTHER
        }
    }

    object FileType {
        const val IMAGE = 0
        const val VIDEO = 1
        const val AUDIO = 2
        const val PDF = 3
        const val WORD = 4
        const val EXCEL = 5
        const val PPT = 6
        const val TEXT = 7
        const val ARCHIVE = 8
        const val APK = 9
        const val OTHER = 10
        const val DIRECTORY = 11
    }

    /**
     * 判断是否是媒体文件
     */
    fun isMediaFile(fileName: String): Boolean {
        val type = getFileTypeIcon(fileName)
        return type in setOf(FileType.IMAGE, FileType.VIDEO, FileType.AUDIO)
    }

    /**
     * 判断是否是文本文件
     */
    fun isTextFile(fileName: String): Boolean {
        val type = getFileTypeIcon(fileName)
        return type in setOf(FileType.TEXT, FileType.PDF, FileType.WORD, FileType.EXCEL, FileType.PPT)
    }
}
