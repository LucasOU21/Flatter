package com.example.flatter.chatVista

data class Chat(
    val id: String = "",
    val participants: List<String> = emptyList(),
    val createdAt: com.google.firebase.Timestamp = com.google.firebase.Timestamp.now(),
    val lastMessage: String = "",
    val lastMessageTimestamp: com.google.firebase.Timestamp = com.google.firebase.Timestamp.now()
)