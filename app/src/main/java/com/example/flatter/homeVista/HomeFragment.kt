package com.example.flatter.homeVista

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.flatter.R
import com.example.flatter.chatVista.ContactDialog
import com.example.flatter.databinding.FragmentHomeBinding
import com.example.flatter.utils.FlatterToast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.NumberFormat
import java.util.Locale

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var listingImageAdapter: ListingImageAdapter
    private var currentListingIndex = 0
    private var listings = mutableListOf<ListingModel>()
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val TAG = "HomeFragment"

    private var lastVisibleDocument: com.google.firebase.firestore.DocumentSnapshot? = null

    //flag to track if we're loading the first page
    private var isInitialLoad = true

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        Log.d(TAG, "onViewCreated called")

        setupButtons()

        setupMapButton()

        loadListingsFromFirebase()

        debugUserTypesInFirestore()
    }

    override fun onResume() {
        super.onResume()

        if (isAdded && _binding != null) {

            val shouldReload = when {
                listings.isEmpty() -> true

                isInitialLoad -> true
                false -> true //Set to false to prevent automatic reload

                else -> false
            }

            if (shouldReload) {
                Log.d(TAG, "onResume: Reloading listings")
                isInitialLoad = true
                loadListingsFromFirebase()
            } else {
                Log.d(TAG, "onResume: Not reloading listings - preserving current state")
            }
        }
    }

    private fun setupButtons() {
        //Reject button
        binding.btnReject.setOnClickListener {
            if (listings.isNotEmpty() && currentListingIndex < listings.size) {

                currentListingIndex++

                if (currentListingIndex < listings.size) {
                    displayCurrentListing()
                } else {
                    //No more listings, show message
                    showNoMoreListingsMessage()
                }
            }
        }


        binding.btnAccept.setOnClickListener {
            if (listings.isNotEmpty() && currentListingIndex < listings.size) {
                val currentListing = listings[currentListingIndex]


                if (auth.currentUser == null) {
                    Toast.makeText(
                        requireContext(),
                        "Debes iniciar sesión para contactar",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }

                saveLikedListing(currentListing.id)

                showContactDialog(currentListing)
            }
        }
    }

    private fun setupMapButton() {
        binding.btnViewMap.setOnClickListener {
            if (listings.isNotEmpty() && currentListingIndex < listings.size) {
                val currentListing = listings[currentListingIndex]
                openDistrictMap(currentListing)
            } else {
                FlatterToast.showError(requireContext(), "No hay anuncio disponible")
            }
        }
    }

    private fun openDistrictMap(listing: ListingModel) {
        try {
            val district = extractDistrictFromLocation(listing.location)

            Log.d(TAG, "Opening map for district: $district from location: ${listing.location}")

            val intent = Intent(requireContext(), com.example.flatter.maps.DistrictMapActivity::class.java)
            intent.putExtra("DISTRICT_NAME", district)
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening map: ${e.message}")
            FlatterToast.showError(requireContext(), "Error al abrir el mapa: ${e.message}")
        }
    }

    private fun extractDistrictFromLocation(location: String): String {
        //handles formats like "Centro, Madrid", "Eixample, Barcelona", or just "Centro"
        return if (location.contains(",")) {
            location.split(",").firstOrNull()?.trim() ?: "Centro"
        } else {
            location.trim().ifEmpty { "Centro" }
        }
    }

    private fun showContactDialog(listing: ListingModel) {
        val dialog = ContactDialog(
            requireContext(),
            listing,
            viewLifecycleOwner.lifecycleScope
        ) {
            FlatterToast.showSuccess(requireContext(), "Redirigiendo a chats...")
            navigateToChatsFragment()
        }
        dialog.show()
    }

    private fun navigateToChatsFragment() {
        //navigate to Chats fragment using Bottom Navigation
        val bottomNavigation = requireActivity().findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(
            R.id.bottom_navigation
        )
        bottomNavigation.selectedItemId = R.id.navigation_chats
    }

    private fun loadListingsFromFirebase() {
        //only clear listings if this is the initial load
        if (isInitialLoad) {
            showLoading(true)
            listings.clear()
            lastVisibleDocument = null
            currentListingIndex = 0
        }

        val currentUserId = auth.currentUser?.uid

        if (currentUserId == null) {
            loadAllListings()
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val userDoc = db.collection("users")
                    .document(currentUserId)
                    .get()
                    .await()

                val maxBudget = userDoc.getDouble("maxBudget") ?: 0.0
                Log.d(TAG, "User max budget: $maxBudget")

                //f maxBudget is 0, load all listings (no budget filtering)
                if (maxBudget <= 0) {
                    Log.d(TAG, "No budget set, loading all listings")
                    loadAllListings()
                } else {
                    //Load filtered listings by budget
                    loadFilteredListings(maxBudget)
                }
            } catch (e: Exception) {
                showLoading(false)
                FlatterToast.showError(
                    requireContext(),
                    "Error al cargar perfil: ${e.message}"
                )
                loadAllListings()
            }
        }
    }

    private fun loadAllListings() {
        val query = db.collection("listings")
            .limit(20)
        if (!isInitialLoad && lastVisibleDocument != null) {
            val paginatedQuery = query.startAfter(lastVisibleDocument!!)
            executeListingsQuery(paginatedQuery)
        } else {
            executeListingsQuery(query)
        }
    }

    private fun loadFilteredListings(maxBudget: Double) {
        //Get current user ID
        val currentUserId = auth.currentUser?.uid ?: return

        try {
            val query = db.collection("listings")
                .limit(50)

            if (!isInitialLoad && lastVisibleDocument != null) {
                val paginatedQuery = query.startAfter(lastVisibleDocument!!)
                executeListingsQuery(paginatedQuery, currentUserId, maxBudget)
            } else {
                executeListingsQuery(query, currentUserId, maxBudget)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error with filtered query: ${e.message}")

            val fallbackQuery = db.collection("listings")
                .limit(50)

            if (!isInitialLoad && lastVisibleDocument != null) {
                val paginatedFallbackQuery = fallbackQuery.startAfter(lastVisibleDocument!!)
                executeListingsQuery(paginatedFallbackQuery, currentUserId, maxBudget)
            } else {
                executeListingsQuery(fallbackQuery, currentUserId, maxBudget)
            }
        }
    }

    private fun executeListingsQuery(
        query: Query,
        currentUserId: String? = null,
        maxBudget: Double = 0.0
    ) {
        Log.d(TAG, "Executing query with maxBudget: $maxBudget")

        query.get()
            .addOnSuccessListener { documents ->
                Log.d(TAG, "Query returned ${documents.size()} documents")

                if (documents.isEmpty) {
                    if (isInitialLoad) {
                        showNoListingsMessage()
                    } else {
                        Toast.makeText(
                            requireContext(),
                            "No hay más listados disponibles",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    showLoading(false)
                    return@addOnSuccessListener
                }

                if (documents.size() > 0) {
                    lastVisibleDocument = documents.documents[documents.size() - 1]
                }

                val tempListings = mutableListOf<ListingModel>()
                var addedCount = 0

                lifecycleScope.launch {
                    for (document in documents) {
                        try {
                            val id = document.getString("id") ?: document.id
                            val userId = document.getString("userId") ?: ""
                            val price = document.getDouble("price") ?: 0.0

                            if (currentUserId != null && userId == currentUserId) {
                                Log.d(TAG, "Skipping own listing: $id")
                                continue
                            }


                            if (maxBudget > 0 && price > maxBudget) {
                                Log.d(TAG, "Skipping listing $id because price $price is above budget $maxBudget")
                                continue
                            }

                            val title = document.getString("title") ?: ""
                            val description = document.getString("description") ?: ""
                            val location = document.getString("location") ?: ""
                            val bedrooms = document.getLong("bedrooms")?.toInt() ?: 1
                            val bathrooms = document.getLong("bathrooms")?.toInt() ?: 1
                            val area = document.getLong("area")?.toInt() ?: 0

                            val imagesList = document.get("imageUrls") as? List<String> ?: listOf()

                            val userName = document.getString("userName") ?: "Usuario"
                            val userProfileImageUrl = document.getString("userProfileImageUrl") ?: ""
                            val publishedDate = document.getString("publishedDate") ?: ""
                            var userType = document.getString("userType")

                            if (userType.isNullOrEmpty() && userId.isNotEmpty()) {
                                try {
                                    val userDoc = db.collection("users").document(userId).get().await()
                                    userType = userDoc.getString("userType") ?: "propietario"
                                    Log.d(TAG, "Fetched user type for user $userId: $userType")
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error fetching user type for user $userId: ${e.message}")
                                    userType = "propietario"
                                }
                            }

                            if (userType.isNullOrEmpty()) {
                                userType = "propietario"
                            }

                            Log.d(TAG, "Listing $id - User: $userName, UserType: $userType")

                            val listing = ListingModel(
                                id = id,
                                title = title,
                                description = description,
                                price = price,
                                location = location,
                                bedrooms = bedrooms,
                                bathrooms = bathrooms,
                                area = area,
                                imageUrls = imagesList,
                                userId = userId,
                                userName = userName,
                                userProfileImageUrl = userProfileImageUrl,
                                publishedDate = publishedDate,
                                userType = userType
                            )

                            tempListings.add(listing)
                            addedCount++
                            Log.d(TAG, "Added listing: $id with userType: $userType")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing document: ${e.message}")
                            continue
                        }
                    }

                    //always randomize the order of the listings
                    tempListings.shuffle()
                    Log.d(TAG, "Shuffled the listings for random order")

                    if (isInitialLoad) {

                        listings.clear()
                        listings.addAll(tempListings)
                    } else {
                        listings.addAll(tempListings)
                        listings.shuffle()
                        Log.d(TAG, "Reshuffled all listings after pagination")
                    }

                    Log.d(TAG, "Processed ${documents.size()} documents, added $addedCount listings. Total listings now: ${listings.size}")

                    if (listings.isNotEmpty()) {
                        if (isInitialLoad) {
                            currentListingIndex = 0
                            displayCurrentListing()
                        } else if (addedCount > 0) {

                            displayCurrentListing()
                        } else {
                            Toast.makeText(
                                requireContext(),
                                "No hay más anuncios disponibles",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } else {
                        Log.d(TAG, "No listings to display after filtering")
                        if (isInitialLoad) {
                            showNoListingsMessage()
                        } else {
                            Toast.makeText(
                                requireContext(),
                                "No hay más anuncios disponibles",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                    isInitialLoad = false
                    showLoading(false)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error loading listings: ${e.message}")
                Toast.makeText(
                    requireContext(),
                    "Error al cargar listados: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
                showLoading(false)
                if (isInitialLoad) {
                    showNoListingsMessage()
                }
            }
    }

    private fun displayCurrentListing() {
        if (listings.isEmpty() || currentListingIndex >= listings.size) {
            showNoMoreListingsMessage()
            return
        }

        val listing = listings[currentListingIndex]

        binding.cardListing.visibility = View.VISIBLE

        binding.tvTitle.text = listing.title
        binding.tvLocation.text = listing.location
        binding.tvDescription.text = listing.description
        binding.tvUserName.text = getString(R.string.publicado_por, listing.userName)
        binding.tvPublishedDate.text = getString(R.string.publicado_fecha, listing.publishedDate)

        //set user type badge with proper validation
        val userTypeText = when (listing.userType.lowercase().trim()) {
            "inquilino" -> {
                Log.d(TAG, "Displaying INQUILINO badge for user: ${listing.userName}")
                "Inquilino"
            }
            "propietario" -> {
                Log.d(TAG, "Displaying PROPIETARIO badge for user: ${listing.userName}")
                "Propietario"
            }
            else -> {
                Log.w(TAG, "Unknown user type '${listing.userType}' for user: ${listing.userName}, defaulting to Propietario")
                "Propietario" // Default
            }
        }

        binding.tvUserTypeBadge.text = userTypeText

        //set user type badge background color based on type
        val badgeBackground = when (listing.userType.lowercase().trim()) {
            "inquilino" -> {
                Log.d(TAG, "Setting RENTER badge background")
                R.drawable.bg_user_type_badge_renter
            }
            "propietario" -> {
                Log.d(TAG, "Setting OWNER badge background")
                R.drawable.bg_user_type_badge_owner
            }
            else -> {
                Log.d(TAG, "Setting DEFAULT badge background")
                R.drawable.bg_user_type_badge_owner
            }
        }
        binding.tvUserTypeBadge.setBackgroundResource(badgeBackground)
        val formattedPrice = NumberFormat.getCurrencyInstance(Locale("es", "ES"))
            .format(listing.price)
        binding.tvPrice.text = getString(R.string.precio_por_mes, formattedPrice)
        binding.tvBedrooms.text = resources.getQuantityString(
            R.plurals.bedroom_count,
            listing.bedrooms,
            listing.bedrooms
        )
        binding.tvBathrooms.text = resources.getQuantityString(
            R.plurals.bathroom_count,
            listing.bathrooms,
            listing.bathrooms
        )
        binding.tvArea.text = getString(R.string.area_metros, listing.area)

        //load user profile image
        Glide.with(requireContext())
            .load(listing.userProfileImageUrl)
            .placeholder(R.drawable.default_profile_img)
            .error(R.drawable.default_profile_img)
            .into(binding.ivUserProfile)

        //setup image carousel
        setupImageCarousel(listing.imageUrls)
        Log.d(TAG, "Current listing - User: ${listing.userName}, UserType: '${listing.userType}', Badge: $userTypeText")
    }

    private fun setupImageCarousel(imageUrls: List<String>) {
        //create adapter
        listingImageAdapter = ListingImageAdapter(imageUrls)
        binding.viewPagerImages.adapter = listingImageAdapter

        //setup dots indicator
        setupDotsIndicator(imageUrls.size)

        //handle page changes
        binding.viewPagerImages.registerOnPageChangeCallback(object :
            androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updateDotsIndicator(position)
            }
        })
    }

    private fun setupDotsIndicator(count: Int) {
        binding.dotsIndicator.removeAllViews()

        //create indicator dots
        for (i in 0 until count) {
            val dot = android.widget.ImageView(requireContext())
            dot.setImageResource(
                if (i == 0) R.drawable.radio_button_checked_24
                else R.drawable.radio_button_unchecked_24
            )

            val params = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(8, 0, 8, 0)
            binding.dotsIndicator.addView(dot, params)
        }
    }

    private fun updateDotsIndicator(position: Int) {
        val count = binding.dotsIndicator.childCount

        for (i in 0 until count) {
            val dot = binding.dotsIndicator.getChildAt(i) as android.widget.ImageView
            dot.setImageResource(
                if (i == position) R.drawable.radio_button_checked_24
                else R.drawable.radio_button_unchecked_24
            )
        }
    }

    private fun saveLikedListing(listingId: String) {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return

        val likeData = hashMapOf(
            "userId" to currentUser.uid,
            "listingId" to listingId,
            "timestamp" to com.google.firebase.Timestamp.now()
        )

        db.collection("likes")
            .add(likeData)
            .addOnSuccessListener {
                //use custom success toast
                FlatterToast.showSuccess(requireContext(), "¡Anuncio guardado!")
            }
            .addOnFailureListener { e ->
                //use custom error toast
                FlatterToast.showError(requireContext(), "Error al guardar: ${e.message}")
            }
    }

    private fun showNoMoreListingsMessage() {
        binding.cardListing.visibility = View.INVISIBLE
        Toast.makeText(
            requireContext(),
            getString(R.string.no_listings_available),
            Toast.LENGTH_LONG
        ).show()
        loadMoreListings()
    }

    private fun showNoListingsMessage() {
        binding.cardListing.visibility = View.INVISIBLE
        Toast.makeText(
            requireContext(),
            getString(R.string.no_listings_available),
            Toast.LENGTH_LONG
        ).show()
    }

    //helper function for loading more listings
    private fun loadMoreListings() {
        //if we're already loading, don't trigger another load
        if (binding.progressBar.visibility == View.VISIBLE) {
            return
        }

        Toast.makeText(requireContext(), "Buscando más propiedades...", Toast.LENGTH_SHORT).show()
        isInitialLoad = false

        //show loading indicator
        showLoading(true)

        //reload listings with pagination
        loadListingsFromFirebase()
    }

    private fun showLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnAccept.isEnabled = !isLoading
        binding.btnReject.isEnabled = !isLoading
    }

    private fun debugUserTypesInFirestore() {
        lifecycleScope.launch {
            try {
                //check all users in the database
                val usersSnapshot = db.collection("users").get().await()
                Log.d(TAG, "=== USER TYPE VERIFICATION ===")

                for (userDoc in usersSnapshot.documents) {
                    val userId = userDoc.id
                    val userName = userDoc.getString("fullName") ?: "Unknown"
                    val userType = userDoc.getString("userType")

                    Log.d(TAG, "User: $userName (ID: $userId) - UserType: '$userType'")
                }

                //check all listings in the database
                val listingsSnapshot = db.collection("listings").get().await()
                Log.d(TAG, "=== LISTING USER TYPE VERIFICATION ===")

                for (listingDoc in listingsSnapshot.documents) {
                    val listingId = listingDoc.id
                    val title = listingDoc.getString("title") ?: "Unknown"
                    val userName = listingDoc.getString("userName") ?: "Unknown"
                    val userType = listingDoc.getString("userType")
                    val userId = listingDoc.getString("userId") ?: ""

                    Log.d(TAG, "Listing: $title by $userName (UserID: $userId) - UserType: '$userType'")

                    //if listing doesn't have userType, fetch from user document
                    if (userType.isNullOrEmpty() && userId.isNotEmpty()) {
                        val userDoc = db.collection("users").document(userId).get().await()
                        val actualUserType = userDoc.getString("userType")
                        Log.d(TAG, "  -> Fetched from user doc: '$actualUserType'")
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error during verification: ${e.message}")
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}