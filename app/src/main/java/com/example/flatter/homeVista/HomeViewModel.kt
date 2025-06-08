package com.example.flatter.homeVista

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class HomeViewModel : ViewModel() {

    private val _listados = MutableLiveData<List<ListingModel>>()
    val listados: LiveData<List<ListingModel>> = _listados

    private val _cargando = MutableLiveData<Boolean>()
    val cargando: LiveData<Boolean> = _cargando

    private val _error = MutableLiveData<String>()
    val error: LiveData<String> = _error

    //este sería tu repositorio en una implementación real
    //private val listingRepository = ListingRepository()

    fun cargarListados() {
        _cargando.value = true

        viewModelScope.launch {
            try {
                //en una implementación real, obtendrías los datos desde tu API o Firebase
                //val result = listingRepository.getListings()
                delay(1000)
                val datosSimulados = generarListadosSimulados()
                _listados.value = datosSimulados

                _cargando.value = false
            } catch (e: Exception) {
                _error.value = "Error al cargar listados: ${e.message}"
                _cargando.value = false
            }
        }
    }

    fun cargarMasListados() {
        val listadosActuales = _listados.value ?: emptyList()
        if (listadosActuales.size >= 50) return  // Limitar para demo

        _cargando.value = true

        viewModelScope.launch {
            try {
                //En una implementación real, cargarías más datos desde tu API
                //val result = listingRepository.getMoreListings(lastId)

                delay(1000) // Simular tiempo de carga
                val nuevosDatos = generarListadosSimulados(5)

                _listados.value = listadosActuales + nuevosDatos
                _cargando.value = false
            } catch (e: Exception) {
                _error.value = "Error al cargar más listados: ${e.message}"
                _cargando.value = false
            }
        }
    }

    fun aceptarListado(listingId: String) {
        // En una implementación real, guardarías esta preferencia
        // listingRepository.saveLikedListing(listingId)
        println("Listado aceptado: $listingId")
    }

    fun rechazarListado(listingId: String) {
        //En una implementación real, guardarías esta preferencia
        //listingRepository.saveDislikedListing(listingId)
        println("Listado rechazado: $listingId")
    }

    //Función para generar datos simulados (solo para demo)
    private fun generarListadosSimulados(cantidad: Int = 10): List<ListingModel> {
        val listados = mutableListOf<ListingModel>()

        val ciudades = listOf("Barcelona", "Madrid", "Valencia", "Sevilla", "Bilbao")
        val barrios = listOf("Centro", "Eixample", "Gràcia", "Salamanca", "Malasaña", "Ruzafa")
        val nombres = listOf("Ana", "Carlos", "María", "Javier", "Laura", "Miguel", "Pablo", "Elena")
        val apellidos = listOf("García", "Rodríguez", "López", "Martínez", "Fernández", "González")

        val formatoFecha = SimpleDateFormat("dd/MM/yyyy", Locale("es", "ES"))

        for (i in 1..cantidad) {
            val ciudad = ciudades.random()
            val barrio = barrios.random()
            val nombre = nombres.random()
            val apellido = apellidos.random()

            val habitaciones = (1..4).random()
            val baños = (1..2).random()
            val area = (50..120).random()
            val precio = (400..1200).random().toDouble()

            val cantidadImagenes = (2..5).random()
            val imagenesListado = mutableListOf<String>()

            //URLs de imágenes de ejemplo (reemplazar por tus propias imágenes)
            for (j in 1..cantidadImagenes) {
                imagenesListado.add("https://source.unsplash.com/random/800x600/?apartment,room,house,interior&sig=${UUID.randomUUID()}")
            }

            val fechaPublicada = formatoFecha.format(Date(System.currentTimeMillis() - (1..30).random() * 24 * 60 * 60 * 1000))

            listados.add(
                ListingModel(
                    id = UUID.randomUUID().toString(),
                    title = if (habitaciones == 1) "Habitación en piso compartido en $barrio" else "Piso de $habitaciones habitaciones en $barrio",
                    description = "Bonito ${if (habitaciones == 1) "habitación" else "piso"} ${if (habitaciones > 1) "de $habitaciones habitaciones" else ""} en zona tranquila de $barrio, $ciudad. ${if (habitaciones > 1) "Dispone de $baños ${if (baños == 1) "baño" else "baños"} completos. " else "Baño compartido. "}Cocina totalmente equipada, internet de fibra óptica, calefacción y aire acondicionado. Cerca de transporte público, supermercados y zonas verdes. ${if (habitaciones == 1) "El piso cuenta con zonas comunes amplias, salón con TV y balcón. Se busca persona tranquila, limpia y respetuosa." else "Ideal para familias o compañeros de piso. Se requiere contrato de trabajo y referencias."}",
                    price = precio,
                    location = "$barrio, $ciudad",
                    bedrooms = habitaciones,
                    bathrooms = baños,
                    area = area,
                    imageUrls = imagenesListado,
                    userId = UUID.randomUUID().toString(),
                    userName = "$nombre $apellido",
                    userProfileImageUrl = "https://source.unsplash.com/random/200x200/?portrait&sig=${UUID.randomUUID()}",
                    publishedDate = fechaPublicada
                )
            )
        }

        return listados
    }
}