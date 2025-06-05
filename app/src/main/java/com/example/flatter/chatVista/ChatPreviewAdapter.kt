package com.example.flatter.chatVista

import android.graphics.Color
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.flatter.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatPreviewAdapter(
    private val onChatClicked: (ChatPreview) -> Unit
) : ListAdapter<ChatPreview, ChatPreviewAdapter.ChatViewHolder>(ChatDiffCallback()) {

    class ChatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivProfilePic: ImageView = itemView.findViewById(R.id.ivProfilePic)
        val tvUserName: TextView = itemView.findViewById(R.id.tvUserName)
        val tvLastMessage: TextView = itemView.findViewById(R.id.tvLastMessage)
        val tvTimestamp: TextView = itemView.findViewById(R.id.tvTimestamp)
        val tvUnreadCount: TextView = itemView.findViewById(R.id.tvUnreadCount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_preview, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val chatPreview = getItem(position)

        // Load profile picture
        Glide.with(holder.itemView.context)
            .load(chatPreview.otherUserProfilePic)
            .placeholder(R.drawable.default_profile_img)
            .error(R.drawable.default_profile_img)
            .circleCrop()
            .into(holder.ivProfilePic)

        // Set user name with user type and listing title if available
        val userTypeLabel = when (chatPreview.otherUserType.lowercase()) {
            "inquilino" -> "Inquilino"
            "propietario" -> "Propietario"
            else -> "Propietario" // Default
        }

        val userName = if (chatPreview.listingTitle.isNotEmpty()) {
            "${chatPreview.otherUserName} ($userTypeLabel) - ${chatPreview.listingTitle}"
        } else {
            "${chatPreview.otherUserName} ($userTypeLabel)"
        }
        holder.tvUserName.text = userName

        // Apply different styles based on chat status
        when (chatPreview.status) {
            "pending" -> {
                holder.tvUserName.setTypeface(null, Typeface.BOLD)
                holder.tvLastMessage.text = if (chatPreview.lastMessage.isEmpty()) {
                    "Chat pendiente de aceptación"
                } else {
                    chatPreview.lastMessage
                }
                holder.itemView.setBackgroundColor(ContextCompat.getColor(holder.itemView.context, R.color.colorSurfaceDark))
            }
            "accepted" -> {
                // Normal display for accepted chats
                holder.tvUserName.setTypeface(null, if (chatPreview.unreadCount > 0) Typeface.BOLD else Typeface.NORMAL)
                holder.tvLastMessage.text = if (chatPreview.lastMessage.isEmpty()) {
                    "Comienza una conversación"
                } else {
                    chatPreview.lastMessage
                }
                holder.itemView.setBackgroundColor(Color.TRANSPARENT)
            }
            "declined" -> {
                holder.tvUserName.setTypeface(null, Typeface.NORMAL)
                holder.tvLastMessage.text = "Chat rechazado por el propietario"
                holder.tvLastMessage.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.colorReject))
                holder.itemView.setBackgroundColor(ContextCompat.getColor(holder.itemView.context, R.color.colorSurfaceDark))
            }
            "cancelled" -> {
                holder.tvUserName.setTypeface(null, Typeface.NORMAL)
                holder.tvLastMessage.text = "Chat cancelado"
                holder.tvLastMessage.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.colorReject))
                holder.itemView.setBackgroundColor(ContextCompat.getColor(holder.itemView.context, R.color.colorSurfaceDark))
            }
            else -> {
                // Default display
                holder.tvUserName.setTypeface(null, Typeface.NORMAL)
                holder.tvLastMessage.text = if (chatPreview.lastMessage.isEmpty()) {
                    "Comienza una conversación"
                } else {
                    chatPreview.lastMessage
                }
                holder.itemView.setBackgroundColor(Color.TRANSPARENT)
            }
        }

        // Format and set timestamp
        val formattedDate = if (chatPreview.lastMessageTimestamp.seconds > 0) {
            val date = Date(chatPreview.lastMessageTimestamp.seconds * 1000)
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
        } else {
            ""
        }
        holder.tvTimestamp.text = formattedDate

        // Set unread count
        if (chatPreview.unreadCount > 0 && chatPreview.status == "accepted") {
            holder.tvUnreadCount.visibility = View.VISIBLE
            holder.tvUnreadCount.text = chatPreview.unreadCount.toString()
        } else {
            holder.tvUnreadCount.visibility = View.GONE
        }

        // Set click listener
        holder.itemView.setOnClickListener {
            onChatClicked(chatPreview)
        }
    }

    class ChatDiffCallback : DiffUtil.ItemCallback<ChatPreview>() {
        override fun areItemsTheSame(oldItem: ChatPreview, newItem: ChatPreview): Boolean {
            return oldItem.chatId == newItem.chatId
        }

        override fun areContentsTheSame(oldItem: ChatPreview, newItem: ChatPreview): Boolean {
            return oldItem == newItem
        }
    }
}