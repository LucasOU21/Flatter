package com.example.flatter.maps

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.SeekBar
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
import com.google.android.libraries.places.api.net.PlacesClient
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class DistrictMapActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityDistrictMapBinding
    private lateinit var map: GoogleMap
    private var placesClient: PlacesClient? = null
    private var placesApiAvailable = false

    private var districtName: String = ""

    // Current search center and radius
    private var currentSearchCenter: LatLng? = null
    private var currentRadius: Double = 1000.0 // Default 1km
    private val minRadius = 500.0 // 500m
    private val maxRadius = 5000.0 // 5km

    // UI elements for radius
    private var radiusCircle: Circle? = null
    private var centerMarker: Marker? = null

    // Store markers by type for better organization
    private val markersByType = mutableMapOf<String, MutableList<Marker>>()
    private val typeVisibility = mutableMapOf<String, Boolean>()

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

    // Enhanced fallback data with more locations
    private val fallbackPlaces = mapOf(
        "Centro" to mapOf(
            "Metro" to listOf(
                Triple("Sol", LatLng(40.4169, -3.7035), "Metro L1, L2, L3 - Intercambiador"),
                Triple("Callao", LatLng(40.4199, -3.7077), "Metro L3, L5"),
                Triple("Gran Vía", LatLng(40.4205, -3.7089), "Metro L1, L5"),
                Triple("Ópera", LatLng(40.4180, -3.7122), "Metro L2, L5, R"),
                Triple("Tirso de Molina", LatLng(40.4108, -3.7066), "Metro L1"),
                Triple("La Latina", LatLng(40.4086, -3.7088), "Metro L5"),
                Triple("Sevilla", LatLng(40.4208, -3.7012), "Metro L2"),
                Triple("Banco de España", LatLng(40.4201, -3.6968), "Metro L2")
            ),
            "Renfe" to listOf(
                Triple("Madrid-Sol", LatLng(40.4169, -3.7035), "Cercanías C3, C4"),
                Triple("Recoletos", LatLng(40.4242, -3.6890), "Cercanías C1, C2, C7, C8, C10"),
                Triple("Atocha", LatLng(40.4066, -3.6913), "Cercanías - Estación principal"),
                Triple("Chamartín", LatLng(40.4726, -3.6796), "Cercanías - Estación Norte")
            ),
            "Hospital" to listOf(
                Triple("Hospital Clínico San Carlos", LatLng(40.4448, -3.7187), "Hospital universitario público"),
                Triple("Hospital General Universitario Gregorio Marañón", LatLng(40.4300, -3.6700), "Hospital de referencia"),
                Triple("Hospital Universitario La Paz", LatLng(40.5050, -3.6842), "Hospital público"),
                Triple("Hospital Universitario 12 de Octubre", LatLng(40.3723, -3.6754), "Hospital público"),
                Triple("Hospital Universitario Ramón y Cajal", LatLng(40.5198, -3.6687), "Hospital público"),
                Triple("Hospital Ruber Internacional", LatLng(40.4180, -3.6890), "Hospital privado"),
                Triple("Clínica Universidad de Navarra", LatLng(40.4350, -3.6950), "Hospital privado"),
                Triple("Centro de Salud Embajadores", LatLng(40.4089, -3.7024), "Centro de atención primaria")
            ),
            "Escuela" to listOf(
                Triple("IES Cardenal Cisneros", LatLng(40.4250, -3.7100), "Instituto público"),
                Triple("IES San Isidro", LatLng(40.4140, -3.7080), "Instituto histórico público"),
                Triple("CEIP Menéndez Pelayo", LatLng(40.4120, -3.7010), "Colegio público"),
                Triple("Colegio San Patricio", LatLng(40.4200, -3.7000), "Colegio privado"),
                Triple("Colegio Nuestra Señora del Recuerdo", LatLng(40.4150, -3.6980), "Colegio concertado"),
                Triple("IES Cervantes", LatLng(40.4200, -3.7030), "Instituto público")
            ),
            "Universidad" to listOf(
                Triple("Universidad Complutense de Madrid", LatLng(40.4530, -3.7289), "Universidad pública"),
                Triple("Universidad Autónoma de Madrid", LatLng(40.5445, -3.6967), "Universidad pública"),
                Triple("Universidad Politécnica de Madrid", LatLng(40.4401, -3.6718), "Universidad pública técnica"),
                Triple("Universidad Rey Juan Carlos", LatLng(40.4180, -3.7050), "Universidad pública"),
                Triple("Universidad Pontificia Comillas", LatLng(40.4248, -3.7034), "Universidad privada"),
                Triple("Universidad CEU San Pablo", LatLng(40.4300, -3.6900), "Universidad privada"),
                Triple("IE University", LatLng(40.4250, -3.6950), "Universidad privada internacional")
            ),
            "Supermercado" to listOf(
                Triple("Mercadona Sol", LatLng(40.4160, -3.7040), "Supermercado cadena"),
                Triple("Carrefour Express Gran Vía", LatLng(40.4180, -3.7060), "Supermercado express"),
                Triple("Día Market Embajadores", LatLng(40.4150, -3.7080), "Supermercado descuento"),
                Triple("Lidl Centro", LatLng(40.4190, -3.7020), "Supermercado alemán"),
                Triple("Eroski City", LatLng(40.4170, -3.7030), "Supermercado vasco"),
                Triple("Simply Basic", LatLng(40.4140, -3.7060), "Supermercado básico"),
                Triple("Sánchez Romero", LatLng(40.4200, -3.6980), "Supermercado gourmet"),
                Triple("El Corte Inglés Alimentación", LatLng(40.4200, -3.7077), "Supermercado premium")
            ),
            "Restaurante" to listOf(
                Triple("Botín", LatLng(40.4146, -3.7065), "Restaurante histórico - cochinillo"),
                Triple("Casa Lucio", LatLng(40.4120, -3.7089), "Restaurante tradicional - huevos rotos"),
                Triple("Lhardy", LatLng(40.4170, -3.7030), "Restaurante clásico madrileño"),
                Triple("Casa Revuelta", LatLng(40.4140, -3.7070), "Tapas tradicionales - bacalao"),
                Triple("Taberna El Sur", LatLng(40.4130, -3.7050), "Tapas andaluzas"),
                Triple("Mercado de San Miguel", LatLng(40.4157, -3.7088), "Mercado gourmet"),
                Triple("La Barraca", LatLng(40.4160, -3.7080), "Paella valenciana"),
                Triple("Asador Donostiarra", LatLng(40.4180, -3.7040), "Asador vasco"),
                Triple("Casa Mingo", LatLng(40.4250, -3.7150), "Sidra asturiana y pollo"),
                Triple("Taberna La Bola", LatLng(40.4155, -3.7095), "Cocido madrileño"),
                Triple("El Anciano Rey de los Vinos", LatLng(40.4135, -3.7075), "Taberna histórica"),
                Triple("Casa González", LatLng(40.4190, -3.7010), "Delicatessen y vinos"),
                Triple("McDonald's Sol", LatLng(40.4165, -3.7038), "Comida rápida"),
                Triple("Burger King Gran Vía", LatLng(40.4195, -3.7070), "Comida rápida"),
                Triple("Starbucks Callao", LatLng(40.4198, -3.7075), "Café americano"),
                Triple("Rodilla Sol", LatLng(40.4168, -3.7036), "Bocadillos y café")
            ),
            "Centro Comercial" to listOf(
                Triple("El Corte Inglés Callao", LatLng(40.4200, -3.7077), "Grandes almacenes principales"),
                Triple("El Corte Inglés Preciados", LatLng(40.4185, -3.7045), "Grandes almacenes centro"),
                Triple("Fnac Callao", LatLng(40.4195, -3.7075), "Electrónicos y cultura"),
                Triple("Primark Gran Vía", LatLng(40.4202, -3.7080), "Moda low-cost"),
                Triple("Zara Gran Vía", LatLng(40.4198, -3.7072), "Moda española"),
                Triple("H&M Sol", LatLng(40.4167, -3.7037), "Moda sueca"),
                Triple("Apple Store Puerta del Sol", LatLng(40.4169, -3.7033), "Tecnología Apple")
            ),
            "Parque" to listOf(
                Triple("Parque del Retiro", LatLng(40.4096, -3.6831), "Parque histórico principal"),
                Triple("Plaza Mayor", LatLng(40.4155, -3.7074), "Plaza histórica central"),
                Triple("Plaza de España", LatLng(40.4238, -3.7122), "Plaza con jardines"),
                Triple("Jardines de Sabatini", LatLng(40.4198, -3.7142), "Jardines del Palacio Real"),
                Triple("Campo del Moro", LatLng(40.4177, -3.7165), "Jardines históricos"),
                Triple("Plaza de Oriente", LatLng(40.4184, -3.7139), "Plaza real con jardines"),
                Triple("Parque de las Vistillas", LatLng(40.4120, -3.7110), "Parque con vistas")
            )
        ),
        // Add similar comprehensive data for other districts
        "Salamanca" to mapOf(
            "Metro" to listOf(
                Triple("Serrano", LatLng(40.4309, -3.6896), "Metro L4"),
                Triple("Velázquez", LatLng(40.4242, -3.6842), "Metro L4"),
                Triple("Goya", LatLng(40.4270, -3.6781), "Metro L2, L4"),
                Triple("Príncipe de Vergara", LatLng(40.4319, -3.6751), "Metro L2, L9")
            ),
            "Renfe" to listOf(
                Triple("Recoletos", LatLng(40.4242, -3.6890), "Cercanías C1, C2, C7, C8, C10")
            ),
            "Universidad" to listOf(
                Triple("IE University Madrid", LatLng(40.4250, -3.6950), "Universidad privada de negocios"),
                Triple("Centro de Estudios Garrigues", LatLng(40.4280, -3.6920), "Escuela de derecho")
            ),
            "Supermercado" to listOf(
                Triple("El Corte Inglés Gourmet", LatLng(40.4300, -3.6900), "Supermercado premium"),
                Triple("Sánchez Romero Serrano", LatLng(40.4320, -3.6880), "Supermercado gourmet"),
                Triple("Mercadona Salamanca", LatLng(40.4290, -3.6870), "Supermercado cadena")
            ),
            "Restaurante" to listOf(
                Triple("Ramón Freixa Madrid", LatLng(40.4280, -3.6890), "Restaurante Michelin"),
                Triple("Punto MX", LatLng(40.4300, -3.6910), "Cocina mexicana alta"),
                Triple("José Luis", LatLng(40.4270, -3.6850), "Tapas gourmet"),
                Triple("Horcher", LatLng(40.4250, -3.6880), "Cocina centroeuropea"),
                Triple("Ten con Ten", LatLng(40.4290, -3.6870), "Cocina moderna")
            )
        )
    )

    private fun getSearchQueries(typeName: String): List<String> {
        return when (typeName) {
            "Metro" -> listOf(
                "estacion metro madrid", "metro station madrid", "subway madrid",
                "metro línea", "estación de metro", "transporte público metro"
            )
            "Renfe" -> listOf(
                "estacion renfe madrid", "cercanías madrid", "tren madrid",
                "estación de tren", "renfe cercanías", "estación ferrocarril"
            )
            "Hospital" -> listOf(
                "hospital madrid", "centro médico madrid", "clínica madrid",
                "hospital público", "hospital privado", "centro salud",
                "hospital universitario", "urgencias madrid"
            )
            "Escuela" -> listOf(
                "colegio madrid", "escuela madrid", "instituto madrid",
                "educación madrid", "centro educativo madrid", "IES madrid"
            )
            "Universidad" -> listOf(
                "universidad madrid", "facultad madrid", "campus universitario madrid",
                "universidad pública madrid", "universidad privada madrid", "escuela superior"
            )
            "Supermercado" -> listOf(
                "supermercado madrid", "mercadona madrid", "carrefour madrid",
                "lidl madrid", "día madrid", "eroski madrid", "grocery madrid",
                "alimentación madrid", "hipermercado madrid"
            )
            "Restaurante" -> listOf(
                "restaurante madrid", "bar madrid", "tapas madrid",
                "cafetería madrid", "comida madrid", "cocina madrid",
                "taberna madrid", "gastrobar madrid", "marisquería madrid",
                "pizzería madrid", "hamburguesa madrid", "sushi madrid",
                "comida rápida madrid", "mcdonalds madrid", "burger king madrid"
            )
            "Centro Comercial" -> listOf(
                "centro comercial madrid", "shopping madrid", "grandes almacenes madrid",
                "el corte inglés madrid", "tienda madrid", "mall madrid"
            )
            "Parque" -> listOf(
                "parque madrid", "jardín madrid", "plaza madrid",
                "zona verde madrid", "área recreativa madrid", "espacio público madrid"
            )
            else -> listOf(typeName.lowercase())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDistrictMapBinding.inflate(layoutInflater)
        setContentView(binding.root)

        districtName = intent.getStringExtra("DISTRICT_NAME") ?: "Centro"

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Barrio: $districtName"

        initializePlacesAPI()

        initializeTypeVisibility()

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        setupRadiusControl()

        setupLegendListeners()
    }

    private fun initializePlacesAPI() {
        val apiKey = getString(R.string.google_maps_key)
        if (apiKey == "YOUR_API_KEY") {
            Log.w("DistrictMapActivity", "Google Maps API key not configured, using fallback data")
            placesApiAvailable = false
        } else {
            try {
                if (!Places.isInitialized()) {
                    Places.initialize(applicationContext, apiKey)
                }
                placesClient = Places.createClient(this)
                placesApiAvailable = true
                Log.d("DistrictMapActivity", "Places API initialized successfully")
            } catch (e: Exception) {
                Log.e("DistrictMapActivity", "Error initializing Places API: ${e.message}")
                placesApiAvailable = false
            }
        }
    }

    private fun initializeTypeVisibility() {
        // Start with all types HIDDEN
        typeVisibility["Metro"] = false
        typeVisibility["Renfe"] = false
        typeVisibility["Hospital"] = false
        typeVisibility["Escuela"] = false
        typeVisibility["Universidad"] = false
        typeVisibility["Supermercado"] = false
        typeVisibility["Restaurante"] = false
        typeVisibility["Centro Comercial"] = false
        typeVisibility["Parque"] = false
    }

    private fun setupRadiusControl() {
        // Setup radius SeekBar
        binding.radiusSeekBar.max = ((maxRadius - minRadius) / 100).toInt() // Steps of 100m
        binding.radiusSeekBar.progress = ((currentRadius - minRadius) / 100).toInt()

        updateRadiusLabel()

        binding.radiusSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    currentRadius = minRadius + (progress * 100)
                    updateRadiusLabel()
                    updateRadiusCircle()
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                // Reload places with new radius
                currentSearchCenter?.let { center ->
                    searchPlacesAtLocation(center)
                }
            }
        })
    }

    private fun updateRadiusLabel() {
        val radiusKm = currentRadius / 1000.0
        binding.tvRadiusLabel.text = "Radio: ${String.format("%.1f", radiusKm)} km"
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap

        // Enable zoom controls
        map.uiSettings.isZoomControlsEnabled = true
        map.uiSettings.isMyLocationButtonEnabled = false

        // Set map style
        try {
            map.setMapStyle(
                MapStyleOptions.loadRawResourceStyle(
                    this, R.raw.map_style_light
                )
            )
        } catch (e: Exception) {
            Log.w("DistrictMapActivity", "Could not load map style, using default")
        }

        // Set up map click listener
        map.setOnMapClickListener { latLng ->
            searchPlacesAtLocation(latLng)
        }

        // Find and highlight the district
        val districtCoordinates = madridDistricts[districtName] ?: madridDistricts["Centro"]!!

        // Move camera to district
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(districtCoordinates, 13f))

        // Add district boundary
        addDistrictBoundary(districtCoordinates)

        // Set initial search center to district center
        currentSearchCenter = districtCoordinates
        updateRadiusCircle()

        // Show instruction
        FlatterToast.showLong(this, "Toca el mapa para buscar lugares cercanos. Usa los controles para mostrar/ocultar categorías.")
    }

    private fun addDistrictBoundary(center: LatLng) {
        // Add a light boundary for the district
        map.addCircle(
            CircleOptions()
                .center(center)
                .radius(2500.0) // 2.5km radius for district
                .strokeColor(Color.argb(50, 108, 99, 255))
                .fillColor(Color.argb(10, 108, 99, 255))
                .strokeWidth(2f)
        )
    }

    private fun updateRadiusCircle() {
        currentSearchCenter?.let { center ->
            // Remove existing circle and marker
            radiusCircle?.remove()
            centerMarker?.remove()

            // Add new radius circle
            radiusCircle = map.addCircle(
                CircleOptions()
                    .center(center)
                    .radius(currentRadius)
                    .strokeColor(Color.argb(150, 255, 0, 0)) // Red border
                    .fillColor(Color.argb(30, 255, 0, 0)) // Light red fill
                    .strokeWidth(3f)
            )

            // Add center marker
            centerMarker = map.addMarker(
                MarkerOptions()
                    .position(center)
                    .title("Área de búsqueda")
                    .snippet("Radio: ${String.format("%.1f", currentRadius / 1000.0)} km")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
            )
        }
    }

    private fun searchPlacesAtLocation(location: LatLng) {
        currentSearchCenter = location
        updateRadiusCircle()

        // Clear existing place markers
        clearAllPlaceMarkers()

        // Show loading
        binding.progressBar.visibility = View.VISIBLE

        if (placesApiAvailable) {
            loadNearbyPlacesFromAPI(location)
        } else {
            loadFallbackPlaces(location)
        }
    }

    private fun clearAllPlaceMarkers() {
        for ((_, markers) in markersByType) {
            markers.forEach { it.remove() }
            markers.clear()
        }
    }

    private fun loadNearbyPlacesFromAPI(center: LatLng) {
        if (placesClient == null) {
            loadFallbackPlaces(center)
            return
        }

        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            var apiWorked = false

            try {
                // Test API with a simple query
                val testQuery = "restaurante madrid"
                val autocompleteRequest = com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest.builder()
                    .setQuery(testQuery)
                    .setLocationBias(
                        com.google.android.libraries.places.api.model.RectangularBounds.newInstance(
                            LatLng(center.latitude - 0.02, center.longitude - 0.02),
                            LatLng(center.latitude + 0.02, center.longitude + 0.02)
                        )
                    )
                    .build()

                val testResponse = placesClient!!.findAutocompletePredictions(autocompleteRequest).await()

                if (testResponse.autocompletePredictions.isNotEmpty()) {
                    Log.d("DistrictMapActivity", "Places API working, loading comprehensive data")
                    apiWorked = true

                    // Load all place types with enhanced search
                    val placeTypes = mapOf(
                        "Metro" to BitmapDescriptorFactory.HUE_BLUE,
                        "Renfe" to BitmapDescriptorFactory.HUE_VIOLET,
                        "Hospital" to BitmapDescriptorFactory.HUE_RED,
                        "Escuela" to BitmapDescriptorFactory.HUE_ORANGE,
                        "Universidad" to BitmapDescriptorFactory.HUE_MAGENTA,
                        "Supermercado" to BitmapDescriptorFactory.HUE_GREEN,
                        "Restaurante" to BitmapDescriptorFactory.HUE_AZURE,
                        "Centro Comercial" to BitmapDescriptorFactory.HUE_CYAN,
                        "Parque" to BitmapDescriptorFactory.HUE_GREEN
                    )

                    for ((typeName, markerColor) in placeTypes) {
                        loadPlacesWithEnhancedSearch(center, markerColor, typeName)
                    }

                    FlatterToast.showShort(this@DistrictMapActivity, "Lugares reales cargados desde Google Places")
                }

            } catch (e: Exception) {
                Log.w("DistrictMapActivity", "Places API test failed: ${e.message}")
                apiWorked = false
            }

            if (!apiWorked) {
                Log.d("DistrictMapActivity", "Falling back to comprehensive example data")
                loadFallbackPlaces(center)
            }

            binding.progressBar.visibility = View.GONE
        }
    }

    // FIXED: Added the missing loadFallbackPlaces method
    private fun loadFallbackPlaces(center: LatLng) {
        binding.progressBar.visibility = View.VISIBLE

        Log.d("DistrictMapActivity", "Loading fallback places for ${districtName}")

        // Get fallback data for this district, or use Centro as default
        val districtPlaces = fallbackPlaces[districtName] ?: fallbackPlaces["Centro"] ?: emptyMap()

        val colorMap = mapOf(
            "Metro" to BitmapDescriptorFactory.HUE_BLUE,
            "Renfe" to BitmapDescriptorFactory.HUE_VIOLET,
            "Hospital" to BitmapDescriptorFactory.HUE_RED,
            "Escuela" to BitmapDescriptorFactory.HUE_ORANGE,
            "Universidad" to BitmapDescriptorFactory.HUE_MAGENTA,
            "Supermercado" to BitmapDescriptorFactory.HUE_GREEN,
            "Restaurante" to BitmapDescriptorFactory.HUE_AZURE,
            "Centro Comercial" to BitmapDescriptorFactory.HUE_CYAN,
            "Parque" to BitmapDescriptorFactory.HUE_GREEN
        )

        for ((typeName, places) in districtPlaces) {
            val markerColor = colorMap[typeName] ?: BitmapDescriptorFactory.HUE_RED

            // Initialize marker list for this type if not exists
            if (!markersByType.containsKey(typeName)) {
                markersByType[typeName] = mutableListOf()
            }

            for ((placeName, location, description) in places) {
                val marker = map.addMarker(
                    MarkerOptions()
                        .position(location)
                        .title(placeName)
                        .snippet("$typeName - $description")
                        .icon(BitmapDescriptorFactory.defaultMarker(markerColor))
                )
                marker?.let {
                    markersByType[typeName]?.add(it)
                    it.isVisible = typeVisibility[typeName] ?: false
                    Log.d("DistrictMapActivity", "Added fallback marker for $typeName: $placeName")
                }
            }
        }

        binding.progressBar.visibility = View.GONE

        // Show message that we're using fallback data
        FlatterToast.showShort(this, "Mostrando lugares de ejemplo para $districtName")
    }

    private fun loadFallbackPlacesInRadius(center: LatLng) {
        binding.progressBar.visibility = View.VISIBLE

        Log.d("DistrictMapActivity", "Loading fallback places around ${center.latitude}, ${center.longitude}")

        val colorMap = mapOf(
            "Metro" to BitmapDescriptorFactory.HUE_BLUE,
            "Renfe" to BitmapDescriptorFactory.HUE_VIOLET,
            "Hospital" to BitmapDescriptorFactory.HUE_RED,
            "Escuela" to BitmapDescriptorFactory.HUE_ORANGE,
            "Universidad" to BitmapDescriptorFactory.HUE_MAGENTA,
            "Supermercado" to BitmapDescriptorFactory.HUE_GREEN,
            "Restaurante" to BitmapDescriptorFactory.HUE_AZURE,
            "Centro Comercial" to BitmapDescriptorFactory.HUE_CYAN,
            "Parque" to BitmapDescriptorFactory.HUE_GREEN
        )

        // Use fallback data for the closest district
        val closestDistrict = findClosestDistrict(center)
        val districtPlaces = fallbackPlaces[closestDistrict] ?: fallbackPlaces["Centro"] ?: emptyMap()

        for ((typeName, places) in districtPlaces) {
            val markerColor = colorMap[typeName] ?: BitmapDescriptorFactory.HUE_RED

            // Initialize marker list for this type if not exists
            if (!markersByType.containsKey(typeName)) {
                markersByType[typeName] = mutableListOf()
            }

            for ((placeName, location, description) in places) {
                val distance = calculateDistance(center, location)
                if (distance <= currentRadius) {
                    val marker = map.addMarker(
                        MarkerOptions()
                            .position(location)
                            .title(placeName)
                            .snippet("$typeName - $description (${String.format("%.0f", distance)}m)")
                            .icon(BitmapDescriptorFactory.defaultMarker(markerColor))
                    )
                    marker?.let {
                        markersByType[typeName]?.add(it)
                        it.isVisible = typeVisibility[typeName] ?: false
                        Log.d("DistrictMapActivity", "Added fallback marker for $typeName: $placeName")
                    }
                }
            }
        }

        binding.progressBar.visibility = View.GONE
        FlatterToast.showShort(this, "Lugares de ejemplo cargados para ${String.format("%.1f", currentRadius/1000.0)}km")
    }

    private fun findClosestDistrict(location: LatLng): String {
        var closestDistrict = "Centro"
        var minDistance = Float.MAX_VALUE

        for ((district, coordinates) in madridDistricts) {
            val distance = calculateDistance(location, coordinates)
            if (distance < minDistance) {
                minDistance = distance
                closestDistrict = district
            }
        }

        return closestDistrict
    }

    private suspend fun loadPlacesWithEnhancedSearch(center: LatLng, markerColor: Float, typeName: String) {
        try {
            val client = placesClient ?: return

            // Initialize marker list for this type
            if (!markersByType.containsKey(typeName)) {
                markersByType[typeName] = mutableListOf()
            }

            val searchFields = listOf(
                Place.Field.ID,
                Place.Field.NAME,
                Place.Field.LAT_LNG,
                Place.Field.ADDRESS,
                Place.Field.TYPES,
                Place.Field.RATING
            )

            val queries = getSearchQueries(typeName)
            var totalPlacesFound = 0

            // Search with multiple queries to get comprehensive results
            for (query in queries.take(3)) { // Limit to 3 queries per type to avoid quota issues
                try {
                    val autocompleteRequest = com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest.builder()
                        .setQuery(query)
                        .setLocationBias(
                            com.google.android.libraries.places.api.model.RectangularBounds.newInstance(
                                LatLng(center.latitude - 0.03, center.longitude - 0.03),
                                LatLng(center.latitude + 0.03, center.longitude + 0.03)
                            )
                        )
                        .build()

                    val response = client.findAutocompletePredictions(autocompleteRequest).await()

                    // Process up to 10 results per query
                    for (prediction in response.autocompletePredictions.take(10)) {
                        try {
                            val placeRequest = com.google.android.libraries.places.api.net.FetchPlaceRequest.builder(
                                prediction.placeId,
                                searchFields
                            ).build()

                            val placeResponse = client.fetchPlace(placeRequest).await()
                            val place = placeResponse.place

                            place.latLng?.let { location ->
                                val distance = calculateDistance(center, location)
                                if (distance <= currentRadius) {
                                    // Check if we already have this place (avoid duplicates)
                                    val existingMarker = markersByType[typeName]?.find { marker ->
                                        val existingPos = marker.position
                                        calculateDistance(existingPos, location) < 50 // Within 50m
                                    }

                                    if (existingMarker == null) {
                                        val rating = place.rating?.let { " ⭐${String.format("%.1f", it)}" } ?: ""
                                        val marker = map.addMarker(
                                            MarkerOptions()
                                                .position(location)
                                                .title(place.name ?: typeName)
                                                .snippet("$typeName$rating - ${String.format("%.0f", distance)}m")
                                                .icon(BitmapDescriptorFactory.defaultMarker(markerColor))
                                        )
                                        marker?.let {
                                            markersByType[typeName]?.add(it)
                                            it.isVisible = typeVisibility[typeName] ?: false
                                            totalPlacesFound++
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.w("DistrictMapActivity", "Failed to fetch place details: ${e.message}")
                        }
                    }
                } catch (e: Exception) {
                    Log.w("DistrictMapActivity", "Failed autocomplete for query $query: ${e.message}")
                }
            }

            Log.d("DistrictMapActivity", "Loaded $totalPlacesFound places for $typeName")

        } catch (e: Exception) {
            Log.e("DistrictMapActivity", "Enhanced search failed for $typeName: ${e.message}")
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
            togglePlaceVisibility("Metro")
        }

        // Renfe
        binding.legendRenfe.setOnClickListener {
            togglePlaceVisibility("Renfe")
        }

        // Hospital
        binding.legendHospital.setOnClickListener {
            togglePlaceVisibility("Hospital")
        }

        // Escuela (Schools - combined primary, secondary, and others)
        binding.legendSchool.setOnClickListener {
            togglePlaceVisibility("Escuela")
        }

        // Universidad (University)
        binding.legendUniversidad.setOnClickListener {
            togglePlaceVisibility("Universidad")
        }

        // Supermarkets
        binding.legendSupermarket.setOnClickListener {
            togglePlaceVisibility("Supermercado")
        }

        // Restaurants
        binding.legendRestaurant.setOnClickListener {
            togglePlaceVisibility("Restaurante")
        }

        // Malls
        binding.legendMall.setOnClickListener {
            togglePlaceVisibility("Centro Comercial")
        }

        // Parks
        binding.legendPark.setOnClickListener {
            togglePlaceVisibility("Parque")
        }
    }

    private fun togglePlaceVisibility(placeType: String) {
        // Toggle the visibility state for this type
        val currentVisibility = typeVisibility[placeType] ?: false
        val newVisibility = !currentVisibility
        typeVisibility[placeType] = newVisibility

        // Apply visibility to all markers of this type
        markersByType[placeType]?.forEach { marker ->
            marker.isVisible = newVisibility
        }

        val count = markersByType[placeType]?.size ?: 0

        Log.d("DistrictMapActivity", "Toggled $placeType: ${if (newVisibility) "showing" else "hiding"} $count markers")

        // Show feedback
        val action = if (newVisibility) "Mostrando" else "Ocultando"
        Toast.makeText(this, "$action $placeType ($count lugares)", Toast.LENGTH_SHORT).show()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}