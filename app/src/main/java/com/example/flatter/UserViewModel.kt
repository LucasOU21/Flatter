
package com.example.flatter

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

class UserViewModel : ViewModel() {

    private val _userProfile = MutableLiveData<UserModel>()
    val userProfile: LiveData<UserModel> = _userProfile

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String>()
    val error: LiveData<String> = _error

    // Este sería tu repositorio en una implementación real
    // private val userRepository = UserRepository()

    fun cargarPerfilUsuario() {
        _isLoading.value = true

        viewModelScope.launch {
            try {
                // En una implementación real, obtendrías los datos desde tu API o Firebase
                // val result = userRepository.getUserProfile()

                // Para demo, usamos datos simulados
                delay(1000) // Simular tiempo de carga
                val perfilSimulado = generarPerfilSimulado()
                _userProfile.value = perfilSimulado

                _isLoading.value = false
            } catch (e: Exception) {
                _error.value = "Error al cargar perfil: ${e.message}"
                _isLoading.value = false
            }
        }
    }

    fun actualizarPerfil(perfil: UserModel) {
        _isLoading.value = true

        viewModelScope.launch {
            try {
                // En una implementación real, guardarías los datos en tu API o Firebase
                // userRepository.updateUserProfile(perfil)

                // Para demo, solo simulamos una espera
                delay(1000)
                _userProfile.value = perfil
                _isLoading.value = false
            } catch (e: Exception) {
                _error.value = "Error al actualizar perfil: ${e.message}"
                _isLoading.value = false
            }
        }
    }

    //Esto es solo para un demo luego vamos a implemantar traves de firebase
    private fun generarPerfilSimulado(): UserModel {
        return UserModel(
            id = UUID.randomUUID().toString(),
            fullName = "Ana García",
            email = "ana.garcia@ejemplo.com",
            phone = "+34 612 345 678",
            bio = "Estudiante de arquitectura, 25 años. Me gusta el cine, la música y viajar.",
            profileImageUrl = "https://source.unsplash.com/random/200x200/?portrait&sig=${UUID.randomUUID()}",
            maxBudget = 800.0,
            preferences = UserPreferences(
                preferredLocations = listOf("Centro", "Eixample", "Gràcia"),
                wantRoommate = true,
                minBedrooms = 1,
                minBathrooms = 1
            )
        )
    }
}