package com.example.flatter.chatVista

data class ChatPreview(
    val chatId: String = "",
    val otherUserId: String = "",
    val otherUserName: String = "",
    val otherUserProfilePic: String = "",
    val otherUserType: String = "", //Add user type field
    val unreadCount: Int = 0,
    val lastMessage: String = "",
    val lastMessageTimestamp: com.google.firebase.Timestamp = com.google.firebase.Timestamp.now(),
    val listingId: String = "",  //Listing ID
    val listingTitle: String = "", //Listing title
    val status: String = "pending" //(pending, accepted, declined, cancelled)
)