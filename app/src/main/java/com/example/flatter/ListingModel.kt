package com.example.flatter

data class ListingModel(
    val id: String,
    val title: String,
    val description: String,
    val price: Double,
    val location: String,
    val bedrooms: Int,
    val bathrooms: Int,
    val area: Int,
    val imageUrls: List<String>,
    val userId: String,
    val userName: String,
    val userProfileImageUrl: String,
    val publishedDate: String
)