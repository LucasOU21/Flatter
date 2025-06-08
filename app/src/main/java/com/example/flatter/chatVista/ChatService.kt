package com.example.flatter.chatVista

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
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

    //function to create or get a chat between two users
    suspend fun getOrCreateChat(
        otherUserId: String,
        listingId: String = "",
        listingTitle: String = ""
    ): String {
        val currentUserId = auth.currentUser?.uid ?: throw IllegalStateException("User not logged in")

        try {
            val userIds = listOf(currentUserId, otherUserId).sorted()
            val chatId = userIds.joinToString("_")

            //check if chat already exists
            val chatRef = db.collection("chats").document(chatId)
            val chatDoc = chatRef.get().await()

            if (!chatDoc.exists()) {
                Log.d(TAG, "Creating new chat: $chatId between $currentUserId and $otherUserId")

                //get user data for chat previews including user type
                val currentUserData = db.collection("users").document(currentUserId).get().await()
                val otherUserData = db.collection("users").document(otherUserId).get().await()

                if (!currentUserData.exists() || !otherUserData.exists()) {
                    Log.e(TAG, "Cannot create chat - one or both users don't exist")
                    throw IllegalStateException("User data not found")
                }

                //create new chat with listing informatio use Map<String
                val chatData = mapOf<String, Any>(
                    "participants" to userIds,
                    "createdAt" to com.google.firebase.Timestamp.now(),
                    "lastMessage" to "",
                    "lastMessageTimestamp" to com.google.firebase.Timestamp.now(),
                    "listingId" to listingId,
                    "listingTitle" to listingTitle,
                    "status" to "pending"
                )

                //batch write to ensure all operations succeed or fail together
                val batch = db.batch()

                //set the main chat document
                batch.set(chatRef, chatData)

                //create chat preview for current user with listing info
                val currentUserChatRef = db.collection("users").document(currentUserId)
                    .collection("chats").document(chatId)

                //explicitly type as Map<String, Any>
                val currentUserChatPreview = mapOf<String, Any>(
                    "chatId" to chatId,
                    "otherUserId" to otherUserId,
                    "otherUserName" to (otherUserData.getString("fullName") ?: "Usuario"),
                    "otherUserProfilePic" to (otherUserData.getString("profileImageUrl") ?: ""),
                    "otherUserType" to (otherUserData.getString("userType") ?: "propietario"), // Add user type
                    "unreadCount" to 0,
                    "lastMessage" to "",
                    "lastMessageTimestamp" to com.google.firebase.Timestamp.now(),
                    "listingId" to listingId,
                    "listingTitle" to listingTitle,
                    "status" to "pending"
                )
                batch.set(currentUserChatRef, currentUserChatPreview)

                //create chat preview for other user with listing info
                val otherUserChatRef = db.collection("users").document(otherUserId)
                    .collection("chats").document(chatId)

                //explicitly type as Map<String, Any> to avoid type inference issues
                val otherUserChatPreview = mapOf<String, Any>(
                    "chatId" to chatId,
                    "otherUserId" to currentUserId,
                    "otherUserName" to (currentUserData.getString("fullName") ?: "Usuario"),
                    "otherUserProfilePic" to (currentUserData.getString("profileImageUrl") ?: ""),
                    "otherUserType" to (currentUserData.getString("userType") ?: "propietario"), // Add user type
                    "unreadCount" to 0,
                    "lastMessage" to "",
                    "lastMessageTimestamp" to com.google.firebase.Timestamp.now(),
                    "listingId" to listingId,
                    "listingTitle" to listingTitle,
                    "status" to "pending"
                )
                batch.set(otherUserChatRef, otherUserChatPreview)

                //Commit the batch
                batch.commit().await()

                Log.d(TAG, "Successfully created new chat and chat previews")
            } else {
                Log.d(TAG, "Chat already exists: $chatId")
            }

            return chatId
        } catch (e: Exception) {
            Log.e(TAG, "Error in getOrCreateChat: ${e.message}", e)
            throw e
        }
    }

    //Function to send a message in a chat
    suspend fun sendMessage(chatId: String, messageText: String): Boolean {
        val currentUserId = auth.currentUser?.uid ?: throw IllegalStateException("User not logged in")

        try {
            //before sending message, make sure chat documents exist for both users
            val chatDoc = db.collection("chats").document(chatId).get().await()

            if (!chatDoc.exists()) {
                Log.e(TAG, "Chat doesn't exist: $chatId")
                return false
            }

            //get the participants and other chat data
            val participants = chatDoc.get("participants") as? List<String> ?: emptyList()
            val listingId = chatDoc.getString("listingId") ?: ""
            val listingTitle = chatDoc.getString("listingTitle") ?: ""

            //ensure both users have chat documents before trying to update them
            for (userId in participants) {
                val userChatDocRef = db.collection("users").document(userId)
                    .collection("chats").document(chatId)

                val userChatDoc = userChatDocRef.get().await()

                if (!userChatDoc.exists()) {
                    //if the user chat document doesn't exist, create it
                    //get other user info (the participant who isn't this user)
                    val otherUserId = participants.firstOrNull { it != userId } ?: continue
                    val otherUserDoc = db.collection("users").document(otherUserId).get().await()

                    val otherUserName = otherUserDoc.getString("fullName") ?: "Usuario"
                    val otherUserProfilePic = otherUserDoc.getString("profileImageUrl") ?: ""
                    val otherUserType = otherUserDoc.getString("userType") ?: "propietario" // Get user type

                    //create chat preview for this user - explicitly type as Map<String, Any>
                    val chatPreview = mapOf<String, Any>(
                        "chatId" to chatId,
                        "otherUserId" to otherUserId,
                        "otherUserName" to otherUserName,
                        "otherUserProfilePic" to otherUserProfilePic,
                        "otherUserType" to otherUserType, // Add user type
                        "unreadCount" to 0,
                        "lastMessage" to "",
                        "lastMessageTimestamp" to com.google.firebase.Timestamp.now(),
                        "listingId" to listingId,
                        "listingTitle" to listingTitle,
                        "status" to (chatDoc.getString("status") ?: "pending")
                    )

                    userChatDocRef.set(chatPreview).await()
                    Log.d(TAG, "Created missing chat document for user: $userId")
                }
            }

            //create a new message document
            val messageRef = db.collection("chats").document(chatId)
                .collection("messages").document()

            //use explicit Map<String, Any> type
            val messageData = mapOf<String, Any>(
                "id" to messageRef.id,
                "senderId" to currentUserId,
                "text" to messageText,
                "timestamp" to com.google.firebase.Timestamp.now(),
                "read" to false,
                "type" to "text"
            )

            //add the message
            messageRef.set(messageData).await()

            //Update the chat with last message info
            val chatUpdate = mapOf<String, Any>(
                "lastMessage" to messageText,
                "lastMessageTimestamp" to com.google.firebase.Timestamp.now()
            )
            db.collection("chats").document(chatId).update(chatUpdate).await()

            //Get the other user's ID
            val otherUserId = participants.firstOrNull { it != currentUserId } ?: ""

            //update chat previews
            val chatPreviewUpdate = mapOf<String, Any>(
                "lastMessage" to messageText,
                "lastMessageTimestamp" to com.google.firebase.Timestamp.now()
            )

            //pdate current user's chat preview
            db.collection("users").document(currentUserId)
                .collection("chats").document(chatId)
                .set(chatPreviewUpdate, com.google.firebase.firestore.SetOptions.merge()).await()

            //update other user's chat preview and increment unread count
            val otherUserChatPreviewRef = db.collection("users").document(otherUserId)
                .collection("chats").document(chatId)

            val otherUserChatPreview = otherUserChatPreviewRef.get().await()
            val currentUnreadCount = otherUserChatPreview.getLong("unreadCount") ?: 0

            //Create a properly typed map for the update
            val otherUserUpdate = mapOf<String, Any>(
                "lastMessage" to messageText,
                "lastMessageTimestamp" to com.google.firebase.Timestamp.now(),
                "unreadCount" to (currentUnreadCount + 1)
            )

            otherUserChatPreviewRef.set(otherUserUpdate, com.google.firebase.firestore.SetOptions.merge()).await()

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error sending message: ${e.message}", e)
            return false
        }
    }

    //Update chat status (accepted, declined, cancelled)
    suspend fun updateChatStatus(chatId: String, status: String): Boolean {
        try {
            // Update status in main chat document
            db.collection("chats").document(chatId)
                .update("status", status).await()

            val chatDoc = db.collection("chats").document(chatId).get().await()
            val participants = chatDoc.get("participants") as? List<String> ?: emptyList()

            for (userId in participants) {
                db.collection("users").document(userId)
                    .collection("chats").document(chatId)
                    .update("status", status).await()
            }

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error updating chat status: ${e.message}", e)
            return false
        }
    }

    // delete chat for current user
    suspend fun deleteChat(chatId: String): Boolean {
        val currentUserId = auth.currentUser?.uid ?: return false

        try {
            //remove chat from user's chat list
            db.collection("users").document(currentUserId)
                .collection("chats").document(chatId)
                .delete().await()

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting chat: ${e.message}", e)
            return false
        }
    }

    //function to mark messages as read
    suspend fun markMessagesAsRead(chatId: String) {
        val currentUserId = auth.currentUser?.uid ?: return

        try {
            //get unread messages from other users
            val unreadMessages = db.collection("chats").document(chatId)
                .collection("messages")
                .whereEqualTo("read", false)
                .whereNotEqualTo("senderId", currentUserId)
                .get().await()

            //update each message
            val batch = db.batch()
            for (doc in unreadMessages.documents) {
                batch.update(doc.reference, "read", true)
            }
            batch.commit().await()

            //reset unread count in chat preview
            db.collection("users").document(currentUserId)
                .collection("chats").document(chatId)
                .update("unreadCount", 0).await()
        } catch (e: Exception) {
            Log.e(TAG, "Error marking messages as read: ${e.message}", e)
        }
    }

    //function to get all user's chat previews as a flow
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
                            otherUserType = doc.getString("otherUserType") ?: "propietario",
                            unreadCount = doc.getLong("unreadCount")?.toInt() ?: 0,
                            lastMessage = doc.getString("lastMessage") ?: "",
                            lastMessageTimestamp = doc.getTimestamp("lastMessageTimestamp")
                                ?: com.google.firebase.Timestamp.now(),
                            listingId = doc.getString("listingId") ?: "",
                            listingTitle = doc.getString("listingTitle") ?: "",
                            status = doc.getString("status") ?: "pending"
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

    //fnction to get messages in a chat as a flow
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

    //get chat status flow
    fun getChatStatusFlow(chatId: String): Flow<String> = callbackFlow {
        val listenerRegistration = db.collection("chats").document(chatId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error getting chat status: ${error.message}")
                    close(error)
                    return@addSnapshotListener
                }

                val status = snapshot?.getString("status") ?: "pending"
                trySend(status).isSuccess
            }

        awaitClose { listenerRegistration.remove() }
    }
}