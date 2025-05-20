package com.example.flatter.chatVista

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.lifecycle.LifecycleCoroutineScope
import kotlinx.coroutines.launch

class ChatManager(
    private val context: Context,
    private val lifecycleScope: LifecycleCoroutineScope
) {
    private val chatService = ChatService()

    fun startChatWithUser(
        otherUserId: String,
        otherUserName: String,
        otherUserProfilePic: String
    ) {
        lifecycleScope.launch {
            try {
                // Create or get existing chat
                val chatId = chatService.getOrCreateChat(otherUserId)

                // Open conversation activity
                val intent = Intent(context, ConversationActivity::class.java).apply {
                    putExtra("CHAT_ID", chatId)
                    putExtra("OTHER_USER_NAME", otherUserName)
                    putExtra("OTHER_USER_ID", otherUserId)
                    putExtra("OTHER_USER_PROFILE_PIC", otherUserProfilePic)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "Error starting chat: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}