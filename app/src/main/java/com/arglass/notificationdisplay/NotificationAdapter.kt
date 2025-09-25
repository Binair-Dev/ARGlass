package com.arglass.notificationdisplay

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class NotificationAdapter : RecyclerView.Adapter<NotificationAdapter.NotificationViewHolder>() {

    private var notifications = listOf<NotificationListenerService.NotificationData>()
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    fun updateNotifications(newNotifications: List<NotificationListenerService.NotificationData>) {
        android.util.Log.d("NotificationAdapter", "🔄 Mise à jour adapter avec ${newNotifications.size} notifications")
        notifications = newNotifications.sortedByDescending { it.timestamp }
        notifyDataSetChanged()
        android.util.Log.d("NotificationAdapter", "✅ Adapter mis à jour - notifyDataSetChanged() appelé")
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_notification, parent, false)
        return NotificationViewHolder(view)
    }

    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        holder.bind(notifications[position])
    }

    override fun getItemCount(): Int = notifications.size

    class NotificationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvAppName: TextView = itemView.findViewById(R.id.tvAppName)
        private val tvTime: TextView = itemView.findViewById(R.id.tvTime)
        private val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        private val tvContent: TextView = itemView.findViewById(R.id.tvContent)

        private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

        fun bind(notification: NotificationListenerService.NotificationData) {
            tvAppName.text = notification.appName
            tvTime.text = timeFormat.format(Date(notification.timestamp))

            tvTitle.text = notification.title ?: "Notification"
            tvTitle.visibility = if (notification.title.isNullOrEmpty()) View.GONE else View.VISIBLE

            tvContent.text = notification.content ?: ""
            tvContent.visibility = if (notification.content.isNullOrEmpty()) View.GONE else View.VISIBLE

            // Show at least the app name if both title and content are empty
            if (notification.title.isNullOrEmpty() && notification.content.isNullOrEmpty()) {
                tvTitle.text = "Nouvelle notification"
                tvTitle.visibility = View.VISIBLE
            }
        }
    }
}