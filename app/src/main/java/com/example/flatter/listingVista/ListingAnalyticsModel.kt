package com.example.flatter.listingVista

data class ListingAnalyticsModel(
    val id: String,
    val title: String,
    val imageUrl: String,
    val status: String,
    val likes: Int,
    val views: Int,
    val chatCount: Int
)