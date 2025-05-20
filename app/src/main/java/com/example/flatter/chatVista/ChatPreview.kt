package com.example.flatter.chatVista

data class ChatPreview(
    val chatId: String = "",
    val otherUserId: String = "",
    val otherUserName: String = "",
    val otherUserProfilePic: String = "",
    val unreadCount: Int = 0,
    val lastMessage: String = "",
    val lastMessageTimestamp: com.google.firebase.Timestamp = com.google.firebase.Timestamp.now(),
    val listingId: String = "",  // Added listing ID
    val listingTitle: String = "" // Added listing title
)