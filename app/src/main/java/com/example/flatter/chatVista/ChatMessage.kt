package com.example.flatter.chatVista

data class ChatMessage(
    val id: String = "",
    val senderId: String = "",
    val text: String = "",
    val timestamp: com.google.firebase.Timestamp = com.google.firebase.Timestamp.now(),
    val read: Boolean = false,
    val type: String = "text" // Future support for different message types
)