package com.example.flatter

//UN MODEL PARA GUARDAR EL INFO DEL USUARIO

data class UserModel(
    val id: String = "",
    val fullName: String = "",
    val email: String = "",
    val phone: String = "",
    val bio: String = "",
    val profileImageUrl: String = "",
    val maxBudget: Double = 0.0,
    val preferences: UserPreferences = UserPreferences()
)

data class UserPreferences(
    val preferredLocations: List<String> = emptyList(),
    val wantRoommate: Boolean = false,
    val minBedrooms: Int = 1,
    val minBathrooms: Int = 1
)