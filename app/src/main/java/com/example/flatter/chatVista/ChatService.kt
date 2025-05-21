package com.example.flatter.chatVista

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class ChatService {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val TAG = "ChatService"

    // Function to create or get a chat between two users
    suspend fun getOrCreateChat(
        otherUserId: String,
        listingId: String = "",
        listingTitle: String = ""
    ): String {
        val currentUserId = auth.currentUser?.uid ?: throw IllegalStateException("User not logged in")

        // Sort user IDs to ensure consistent chat ID generation
        val userIds = listOf(currentUserId, otherUserId).sorted()
        val chatId = userIds.joinToString("_")

        val chatRef = db.collection("chats").document(chatId)
        val chatDoc = chatRef.get().await()

        if (!chatDoc.exists()) {
            // Create new chat with listing information
            val chatData: Map<String, Any> = mapOf(
                "participants" to userIds,
                "createdAt" to com.google.firebase.Timestamp.now(),
                "lastMessage" to "",
                "lastMessageTimestamp" to com.google.firebase.Timestamp.now(),
                "listingId" to listingId,
                "listingTitle" to listingTitle
            )
            chatRef.set(chatData).await()

            // Get user data for chat previews
            val currentUserData = db.collection("users").document(currentUserId).get().await()
            val otherUserData = db.collection("users").document(otherUserId).get().await()

            // Create chat preview for current user with listing info
            val currentUserChatPreview: Map<String, Any> = mapOf(
                "chatId" to chatId,
                "otherUserId" to otherUserId,
                "otherUserName" to (otherUserData.getString("fullName") ?: "Usuario"),
                "otherUserProfilePic" to (otherUserData.getString("profileImageUrl") ?: ""),
                "unreadCount" to 0,
                "lastMessage" to "",
                "lastMessageTimestamp" to com.google.firebase.Timestamp.now(),
                "listingId" to listingId,
                "listingTitle" to listingTitle
            )
            db.collection("users").document(currentUserId)
                .collection("chats").document(chatId)
                .set(currentUserChatPreview).await()

            // Create chat preview for other user with listing info
            val otherUserChatPreview: Map<String, Any> = mapOf(
                "chatId" to chatId,
                "otherUserId" to currentUserId,
                "otherUserName" to (currentUserData.getString("fullName") ?: "Usuario"),
                "otherUserProfilePic" to (currentUserData.getString("profileImageUrl") ?: ""),
                "unreadCount" to 0,
                "lastMessage" to "",
                "lastMessageTimestamp" to com.google.firebase.Timestamp.now(),
                "listingId" to listingId,
                "listingTitle" to listingTitle
            )
            db.collection("users").document(otherUserId)
                .collection("chats").document(chatId)
                .set(otherUserChatPreview).await()
        }

        return chatId
    }

    // Function to send a message in a chat
    suspend fun sendMessage(chatId: String, messageText: String): Boolean {
        val currentUserId = auth.currentUser?.uid ?: throw IllegalStateException("User not logged in")

        try {
            // Create a new message document
            val messageRef = db.collection("chats").document(chatId)
                .collection("messages").document()

            // FIX: Use Map<String, Any> explicitly
            val messageData: Map<String, Any> = mapOf(
                "id" to messageRef.id,
                "senderId" to currentUserId,
                "text" to messageText,
                "timestamp" to com.google.firebase.Timestamp.now(),
                "read" to false,
                "type" to "text"
            )

            // Add the message
            messageRef.set(messageData).await()

            // Update the chat with last message info - FIX: Use Map<String, Any> explicitly
            val chatUpdate: Map<String, Any> = mapOf(
                "lastMessage" to messageText,
                "lastMessageTimestamp" to com.google.firebase.Timestamp.now()
            )
            db.collection("chats").document(chatId).update(chatUpdate).await()

            // Get the other user's ID
            val chatDoc = db.collection("chats").document(chatId).get().await()
            val participants = chatDoc.get("participants") as? List<String> ?: emptyList()
            val otherUserId = participants.firstOrNull { it != currentUserId } ?: ""

            // Update chat previews - FIX: Use Map<String, Any> explicitly
            val chatPreviewUpdate: Map<String, Any> = mapOf(
                "lastMessage" to messageText,
                "lastMessageTimestamp" to com.google.firebase.Timestamp.now()
            )

            // Update current user's chat preview
            db.collection("users").document(currentUserId)
                .collection("chats").document(chatId)
                .update(chatPreviewUpdate).await()

            // Update other user's chat preview and increment unread count
            val otherUserChatPreviewRef = db.collection("users").document(otherUserId)
                .collection("chats").document(chatId)

            db.runTransaction { transaction ->
                val otherUserChatPreview = transaction.get(otherUserChatPreviewRef)
                val currentUnreadCount = otherUserChatPreview.getLong("unreadCount") ?: 0

                // FIX: Use explicit updates instead of a map
                transaction.update(otherUserChatPreviewRef, "lastMessage", messageText)
                transaction.update(otherUserChatPreviewRef, "lastMessageTimestamp", com.google.firebase.Timestamp.now())
                transaction.update(otherUserChatPreviewRef, "unreadCount", currentUnreadCount + 1)
            }.await()

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error sending message: ${e.message}")
            return false
        }
    }

    // Function to mark messages as read
    suspend fun markMessagesAsRead(chatId: String) {
        val currentUserId = auth.currentUser?.uid ?: return

        try {
            // Get unread messages from other users
            val unreadMessages = db.collection("chats").document(chatId)
                .collection("messages")
                .whereEqualTo("read", false)
                .whereNotEqualTo("senderId", currentUserId)
                .get().await()

            // Update each message
            val batch = db.batch()
            for (doc in unreadMessages.documents) {
                batch.update(doc.reference, "read", true)
            }
            batch.commit().await()

            // Reset unread count in chat preview
            db.collection("users").document(currentUserId)
                .collection("chats").document(chatId)
                .update("unreadCount", 0).await()
        } catch (e: Exception) {
            Log.e(TAG, "Error marking messages as read: ${e.message}")
        }
    }

    // Function to get all user's chat previews as a flow
    fun getUserChatsFlow(): Flow<List<ChatPreview>> = callbackFlow {
        val currentUserId = auth.currentUser?.uid ?: throw IllegalStateException("User not logged in")

        val listenerRegistration = db.collection("users").document(currentUserId)
            .collection("chats")
            .orderBy("lastMessageTimestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e(TAG, "Error getting chats: ${error.message}")
                    close(error)
                    return@addSnapshotListener
                }

                val chatsList = snapshots?.documents?.mapNotNull { doc ->
                    try {
                        ChatPreview(
                            chatId = doc.getString("chatId") ?: "",
                            otherUserId = doc.getString("otherUserId") ?: "",
                            otherUserName = doc.getString("otherUserName") ?: "Usuario",
                            otherUserProfilePic = doc.getString("otherUserProfilePic") ?: "",
                            unreadCount = doc.getLong("unreadCount")?.toInt() ?: 0,
                            lastMessage = doc.getString("lastMessage") ?: "",
                            lastMessageTimestamp = doc.getTimestamp("lastMessageTimestamp")
                                ?: com.google.firebase.Timestamp.now()
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing chat preview: ${e.message}")
                        null
                    }
                } ?: emptyList()

                trySend(chatsList).isSuccess
            }

        awaitClose { listenerRegistration.remove() }
    }

    // Function to get messages in a chat as a flow
    fun getChatMessagesFlow(chatId: String): Flow<List<ChatMessage>> = callbackFlow {
        val listenerRegistration = db.collection("chats").document(chatId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e(TAG, "Error getting messages: ${error.message}")
                    close(error)
                    return@addSnapshotListener
                }

                val messagesList = snapshots?.documents?.mapNotNull { doc ->
                    try {
                        ChatMessage(
                            id = doc.id,
                            senderId = doc.getString("senderId") ?: "",
                            text = doc.getString("text") ?: "",
                            timestamp = doc.getTimestamp("timestamp")
                                ?: com.google.firebase.Timestamp.now(),
                            read = doc.getBoolean("read") ?: false,
                            type = doc.getString("type") ?: "text"
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing message: ${e.message}")
                        null
                    }
                } ?: emptyList()

                trySend(messagesList).isSuccess
            }

        awaitClose { listenerRegistration.remove() }
    }
}