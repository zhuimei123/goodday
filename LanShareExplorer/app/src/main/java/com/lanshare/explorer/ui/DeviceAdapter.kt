package com.lanshare.explorer.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.lanshare.explorer.R
import com.lanshare.explorer.model.LanDevice

/**
 * 局域网设备列表适配器
 */
class DeviceAdapter(
    private val devices: List<LanDevice>,
    private val onClick: (LanDevice) -> Unit
) : RecyclerView.Adapter<DeviceAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.ivDeviceIcon)
        val name: TextView = view.findViewById(R.id.tvDeviceName)
        val ip: TextView = view.findViewById(R.id.tvDeviceIp)
        val status: TextView = view.findViewById(R.id.tvDeviceStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_device, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val device = devices[position]

        holder.name.text = device.hostName.ifBlank { "未知设备" }
        holder.ip.text = device.ipAddress
        holder.status.text = if (device.isOnline) "在线" else "离线"

        holder.icon.setImageResource(
            if (device.isOnline) R.drawable.ic_computer_online
            else R.drawable.ic_computer_offline
        )

        holder.itemView.setOnClickListener { onClick(device) }
    }

    override fun getItemCount() = devices.size
}
