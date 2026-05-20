package com.lanshare.explorer.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.lanshare.explorer.R
import com.lanshare.explorer.model.SmbFileItem
import com.lanshare.explorer.util.Utils

/**
 * 文件列表适配器
 */
class FileAdapter(
    private val items: MutableList<SmbFileItem>,
    private val onItemClick: (SmbFileItem) -> Unit,
    private val onItemLongClick: (SmbFileItem) -> Boolean
) : RecyclerView.Adapter<FileAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.ivFileIcon)
        val name: TextView = view.findViewById(R.id.tvFileName)
        val details: TextView = view.findViewById(R.id.tvFileDetails)
    }

    fun updateItems(newItems: List<SmbFileItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_file, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]

        holder.name.text = item.name

        // 设置图标
        holder.icon.setImageResource(
            if (item.isDirectory) R.drawable.ic_folder
            else getFileIcon(item.name)
        )

        // 设置详情
        holder.details.text = if (item.isDirectory) {
            "文件夹"
        } else {
            "${item.displaySize}  ${Utils.formatTimestamp(item.lastModified)}"
        }

        holder.itemView.setOnClickListener { onItemClick(item) }
        holder.itemView.setOnLongClickListener { onItemLongClick(item) }
    }

    private fun getFileIcon(fileName: String): Int {
        return when (Utils.getFileTypeIcon(fileName)) {
            Utils.FileType.IMAGE -> R.drawable.ic_file_image
            Utils.FileType.VIDEO -> R.drawable.ic_file_video
            Utils.FileType.AUDIO -> R.drawable.ic_file_audio
            Utils.FileType.PDF -> R.drawable.ic_file_pdf
            Utils.FileType.WORD -> R.drawable.ic_file_document
            Utils.FileType.EXCEL -> R.drawable.ic_file_document
            Utils.FileType.PPT -> R.drawable.ic_file_document
            Utils.FileType.TEXT -> R.drawable.ic_file_text
            Utils.FileType.ARCHIVE -> R.drawable.ic_file_archive
            Utils.FileType.APK -> R.drawable.ic_file_apk
            else -> R.drawable.ic_file_default
        }
    }

    override fun getItemCount() = items.size
}
