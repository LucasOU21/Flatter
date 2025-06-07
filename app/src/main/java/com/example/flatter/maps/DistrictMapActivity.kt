package com.example.flatter.maps

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.flatter.R
import com.example.flatter.databinding.ActivityDistrictMapBinding
import com.example.flatter.utils.FlatterToast
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FindCurrentPlaceRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.api.net.SearchByTextRequest
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import android.location.Location

class DistrictMapActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityDistrictMapBinding
    private lateinit var map: GoogleMap
    private lateinit var placesClient: PlacesClient

    private var districtName: String = ""
    private val placeMarkers = mutableListOf<Marker>()

    private val madridDistricts = mapOf(
        "Centro" to LatLng(40.4168, -3.7038),
        "Arganzuela" to LatLng(40.3922, -3.6976),
        "Retiro" to LatLng(40.4096, -3.6831),
        "Salamanca" to LatLng(40.4302, -3.6797),
        "Chamartín" to LatLng(40.4623, -3.6767),
        "Tetuán" to LatLng(40.4596, -3.6975),
        "Chamberí" to LatLng(40.4344, -3.7041),
        "Fuencarral-El Pardo" to LatLng(40.5031, -3.7319),
        "Moncloa-Aravaca" to LatLng(40.4355, -3.7319),
        "Latina" to LatLng(40.3913, -3.7479),
        "Carabanchel" to LatLng(40.3809, -3.7428),
        "Usera" to LatLng(40.3809, -3.7019),
        "Puente de Vallecas" to LatLng(40.3980, -3.6611),
        "Moratalaz" to LatLng(40.4077, -3.6348),
        "Ciudad Lineal" to LatLng(40.4453, -3.6549),
        "Hortaleza" to LatLng(40.4749, -3.6415),
        "Villaverde" to LatLng(40.3470, -3.6931),
        "Villa de Vallecas" to LatLng(40.3792, -3.6214),
        "Vicálvaro" to LatLng(40.4019, -3.6087),
        "San Blas" to LatLng(40.4238, -3.6115),
        "Barajas" to LatLng(40.4760, -3.5799)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDistrictMapBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get district name from intent
        districtName = intent.getStringExtra("DISTRICT_NAME") ?: "Centro"

        // Setup toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Barrio: $districtName"

        // Check if API key is configured
        val apiKey = getString(R.string.google_maps_key)
        if (apiKey == "YOUR_API_KEY") {
            FlatterToast.showError(this, "Google Maps API key no configurada")
            finish()
            return
        }

        // Initialize Places API
        try {
            if (!Places.isInitialized()) {
                Places.initialize(applicationContext, apiKey)
            }
            placesClient = Places.createClient(this)
        } catch (e: Exception) {
            Log.e("DistrictMapActivity", "Error initializing Places API: ${e.message}")
            // Continue without Places API - will still show the map
        }

        // Initialize the map
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // Setup legend click listeners
        setupLegendListeners()
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap

        // Enable zoom controls
        map.uiSettings.isZoomControlsEnabled = true
        map.uiSettings.isMyLocationButtonEnabled = false

        // Set map style (optional - for better visibility)
        try {
            map.setMapStyle(
                MapStyleOptions.loadRawResourceStyle(
                    this, R.raw.map_style_light
                )
            )
        } catch (e: Exception) {
            // If style not found, use default
        }

        // Find and highlight the district
        val districtCoordinates = madridDistricts[districtName] ?: madridDistricts["Centro"]!!

        // Move camera to district
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(districtCoordinates, 14f))

        // Add district boundary (simplified - in real app you'd load actual boundary data)
        addDistrictBoundary(districtCoordinates)

        // Load nearby places
        loadNearbyPlaces(districtCoordinates)
    }

    private fun addDistrictBoundary(center: LatLng) {
        // Add a circle to represent the district area (simplified)
        map.addCircle(
            CircleOptions()
                .center(center)
                .radius(2000.0) // 2km radius
                .strokeColor(Color.argb(100, 108, 99, 255)) // Semi-transparent purple
                .fillColor(Color.argb(30, 108, 99, 255))
                .strokeWidth(3f)
        )

        // Add a marker for district center
        map.addMarker(
            MarkerOptions()
                .position(center)
                .title(districtName)
                .snippet("Centro del barrio")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_VIOLET))
        )
    }

    private fun loadNearbyPlaces(center: LatLng) {
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                // Load different types of places
                loadPlacesByType(center, "subway_station", BitmapDescriptorFactory.HUE_BLUE, "Metro")
                loadPlacesByType(center, "hospital", BitmapDescriptorFactory.HUE_RED, "Hospital")
                loadPlacesByType(center, "school", BitmapDescriptorFactory.HUE_ORANGE, "Escuela")
                loadPlacesByType(center, "supermarket", BitmapDescriptorFactory.HUE_GREEN, "Supermercado")
                loadPlacesByType(center, "restaurant", BitmapDescriptorFactory.HUE_YELLOW, "Restaurante")
                loadPlacesByType(center, "shopping_mall", BitmapDescriptorFactory.HUE_CYAN, "Centro Comercial")
                loadPlacesByType(center, "park", BitmapDescriptorFactory.HUE_GREEN, "Parque")

                binding.progressBar.visibility = View.GONE
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                FlatterToast.showError(this@DistrictMapActivity, "Error cargando lugares: ${e.message}")
            }
        }
    }

    private suspend fun loadPlacesByType(center: LatLng, placeType: String, markerColor: Float, typeName: String) {
        try {
            // Create a search request
            val searchText = "$typeName cerca de $districtName, Madrid"

            // Specify the fields we want to retrieve
            val placeFields = listOf(
                Place.Field.ID,
                Place.Field.NAME,
                Place.Field.LAT_LNG,
                Place.Field.ADDRESS,
                Place.Field.TYPES
            )

            val request = SearchByTextRequest.builder(searchText, placeFields)
                .setMaxResultCount(10)
                .build()

            val response = placesClient.searchByText(request).await()

            response.places.forEach { place ->
                place.latLng?.let { location ->
                    // Only add places within reasonable distance from district center
                    val distance = calculateDistance(center, location)
                    if (distance < 3000) { // Within 3km
                        val marker = map.addMarker(
                            MarkerOptions()
                                .position(location)
                                .title(place.name ?: typeName) // Use 'name' instead of 'displayName'
                                .snippet(place.address ?: "") // Use 'address' instead of 'formattedAddress'
                                .icon(BitmapDescriptorFactory.defaultMarker(markerColor))
                        )
                        marker?.let { placeMarkers.add(it) }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("DistrictMapActivity", "Error loading places for type $typeName: ${e.message}")
            // Handle error silently for individual place types
        }
    }

    private fun calculateDistance(point1: LatLng, point2: LatLng): Float {
        val results = FloatArray(1)
        android.location.Location.distanceBetween(
            point1.latitude, point1.longitude,
            point2.latitude, point2.longitude,
            results
        )
        return results[0]
    }

    private fun setupLegendListeners() {
        // Metro
        binding.legendMetro.setOnClickListener {
            togglePlaceVisibility("Metro", BitmapDescriptorFactory.HUE_BLUE)
        }

        // Hospital
        binding.legendHospital.setOnClickListener {
            togglePlaceVisibility("Hospital", BitmapDescriptorFactory.HUE_RED)
        }

        // Schools
        binding.legendSchool.setOnClickListener {
            togglePlaceVisibility("Escuela", BitmapDescriptorFactory.HUE_ORANGE)
        }

        // Supermarkets
        binding.legendSupermarket.setOnClickListener {
            togglePlaceVisibility("Supermercado", BitmapDescriptorFactory.HUE_GREEN)
        }

        // Restaurants
        binding.legendRestaurant.setOnClickListener {
            togglePlaceVisibility("Restaurante", BitmapDescriptorFactory.HUE_YELLOW)
        }

        // Malls
        binding.legendMall.setOnClickListener {
            togglePlaceVisibility("Centro Comercial", BitmapDescriptorFactory.HUE_CYAN)
        }

        // Parks
        binding.legendPark.setOnClickListener {
            togglePlaceVisibility("Parque", BitmapDescriptorFactory.HUE_GREEN)
        }
    }

    private fun togglePlaceVisibility(placeType: String, markerColor: Float) {
        placeMarkers.forEach { marker ->
            if (marker.snippet?.contains(placeType, ignoreCase = true) == true ||
                marker.title?.contains(placeType, ignoreCase = true) == true) {
                marker.isVisible = !marker.isVisible
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}