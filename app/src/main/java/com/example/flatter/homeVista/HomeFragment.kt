package com.example.flatter.homeVista

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

        // Add debug log
        Log.d("HomeFragment", "onViewCreated called")

        //Configure buttons
        setupButtons()

        //Load listings from Firebase
        loadListingsFromFirebase()
    }

    private fun setupButtons() {
        // Reject button
        binding.btnReject.setOnClickListener {
            if (listings.isNotEmpty() && currentListingIndex < listings.size) {
                // Move to next listing
                currentListingIndex++

                if (currentListingIndex < listings.size) {
                    displayCurrentListing()
                } else {
                    // No more listings, show message
                    showNoMoreListingsMessage()
                }
            }
        }

        // Accept button
        binding.btnAccept.setOnClickListener {
            if (listings.isNotEmpty() && currentListingIndex < listings.size) {
                val currentListing = listings[currentListingIndex]

                // Check if user is logged in
                if (auth.currentUser == null) {
                    Toast.makeText(
                        requireContext(),
                        "Debes iniciar sesión para contactar",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }

                // Save this listing as "liked" in Firestore
                saveLikedListing(currentListing.id)

                // Show contact dialog
                showContactDialog(currentListing)
            }
        }
    }

    private fun showContactDialog(listing: ListingModel) {
        // Create and show contact dialog
        val dialog = ContactDialog(
            requireContext(),
            listing,
            viewLifecycleOwner.lifecycleScope
        ) {
            // Navigate to Chats fragment when message is sent
            navigateToChatsFragment()
        }
        dialog.show()
    }

    private fun navigateToChatsFragment() {
        // Navigate to Chats fragment using Bottom Navigation
        val bottomNavigation = requireActivity().findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(
            R.id.bottom_navigation
        )
        bottomNavigation.selectedItemId = R.id.navigation_chats
    }

    // Store the last document for pagination
    private var lastVisibleDocument: com.google.firebase.firestore.DocumentSnapshot? = null

    // Store filter parameters for pagination
    private var likedListingIds: Set<String> = emptySet()
    private var chatListingIds: Set<String> = emptySet()
    private var chatParticipantIds: Set<String> = emptySet()

    // Flag to track if we're loading the first page or paginating
    private var isInitialLoad = true

    private fun loadListingsFromFirebase() {
        // Only clear listings if this is the initial load (not pagination)
        if (isInitialLoad) {
            showLoading(true)
            listings.clear()
            lastVisibleDocument = null
        }

        // Get current user ID
        val currentUserId = auth.currentUser?.uid

        // If user is not logged in, load all listings
        if (currentUserId == null) {
            loadAllListings()
            return
        }

        // First get the IDs of listings that the user has already liked
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Only fetch filters if this is the initial load or filters are empty
                if (isInitialLoad || likedListingIds.isEmpty()) {
                    // Get user's liked listings
                    val likedListingsSnapshot = db.collection("likes")
                        .whereEqualTo("userId", currentUserId)
                        .get()
                        .await()

                    likedListingIds = likedListingsSnapshot.documents.mapNotNull {
                        it.getString("listingId")
                    }.toSet()

                    // Get user's active chats to filter out listings from users they're already chatting with
                    val userChatsSnapshot = db.collection("users")
                        .document(currentUserId)
                        .collection("chats")
                        .get()
                        .await()

                    // Get the listing IDs associated with active chats
                    chatListingIds = userChatsSnapshot.documents.mapNotNull {
                        it.getString("listingId")
                    }.toSet()

                    // Get all chats to find users the current user is chatting with
                    val tempChatParticipantIds = mutableSetOf<String>()
                    userChatsSnapshot.documents.forEach { chatDoc ->
                        val otherUserId = chatDoc.getString("otherUserId")
                        if (otherUserId != null) {
                            tempChatParticipantIds.add(otherUserId)
                        }
                    }
                    chatParticipantIds = tempChatParticipantIds
                }

                // Load listings excluding those the user has already liked or is chatting about
                loadFilteredListings(likedListingIds, chatListingIds, chatParticipantIds)
            } catch (e: Exception) {
                showLoading(false)
                Toast.makeText(
                    requireContext(),
                    "Error al cargar filtros: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()

                // Fallback to loading all listings
                loadAllListings()
            }
        }
    }

    private fun loadAllListings() {
        // Build query for listings without any filtering
        val query = db.collection("listings")
            .orderBy("createdAt", Query.Direction.DESCENDING) // Most recent first
            .limit(10) // Limit to 10 listings per page

        // Apply pagination if not initial load
        if (!isInitialLoad && lastVisibleDocument != null) {
            // Need to create a new query with startAfter
            val paginatedQuery = query.startAfter(lastVisibleDocument!!)
            executeListingsQuery(paginatedQuery)
        } else {
            executeListingsQuery(query)
        }
    }

    private fun loadFilteredListings(
        likedListingIds: Set<String>,
        chatListingIds: Set<String>,
        chatParticipantIds: Set<String>
    ) {
        // Get current user ID
        val currentUserId = auth.currentUser?.uid ?: return

        try {
            // Base query for listings
            val query = db.collection("listings")
                .whereNotEqualTo("userId", currentUserId) // Exclude user's own listings
                .orderBy("userId") // Required for inequality filter
                .orderBy("createdAt", Query.Direction.DESCENDING) // Most recent first
                .limit(20) // Get more than needed since we'll filter some out

            // Apply pagination if not initial load
            if (!isInitialLoad && lastVisibleDocument != null) {
                // Need to create a new query with startAfter
                val paginatedQuery = query.startAfter(lastVisibleDocument!!)
                executeListingsQuery(paginatedQuery, likedListingIds, chatListingIds, chatParticipantIds)
            } else {
                executeListingsQuery(query, likedListingIds, chatListingIds, chatParticipantIds)
            }
        } catch (e: Exception) {
            // If there's an error with the compound query (missing index), fall back to a simpler query
            Log.w("HomeFragment", "Error with filtered query, falling back to simple query: ${e.message}")

            // Simple query as fallback
            val fallbackQuery = db.collection("listings")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(20)

            if (!isInitialLoad && lastVisibleDocument != null) {
                val paginatedFallbackQuery = fallbackQuery.startAfter(lastVisibleDocument!!)
                executeListingsQuery(paginatedFallbackQuery, likedListingIds, chatListingIds, chatParticipantIds)
            } else {
                executeListingsQuery(fallbackQuery, likedListingIds, chatListingIds, chatParticipantIds)
            }
        }
    }

    private fun executeListingsQuery(
        query: Query,
        likedListingIds: Set<String> = emptySet(),
        chatListingIds: Set<String> = emptySet(),
        chatParticipantIds: Set<String> = emptySet()
    ) {
        Log.d("HomeFragment", "Executing query...")

        query.get()
            .addOnSuccessListener { documents ->
                Log.d("HomeFragment", "Query returned ${documents.size()} documents")

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

                // Save the last document for pagination
                if (documents.size() > 0) {
                    lastVisibleDocument = documents.documents[documents.size() - 1]
                }

                // Parse documents into ListingModel objects
                var addedCount = 0
                for (document in documents) {
                    try {
                        val id = document.getString("id") ?: document.id
                        val userId = document.getString("userId") ?: ""

                        // If userId is the current user's ID, log and skip
                        if (userId == auth.currentUser?.uid) {
                            Log.d("HomeFragment", "Skipping own listing: $id")
                            continue
                        }

                        // Skip if this listing is already liked
                        if (likedListingIds.isNotEmpty() && id in likedListingIds) {
                            Log.d("HomeFragment", "Skipping listing $id because it's already liked")
                            continue
                        }

                        // Skip if this listing is already in a chat
                        if (chatListingIds.isNotEmpty() && id in chatListingIds) {
                            Log.d("HomeFragment", "Skipping listing $id because it's already in a chat")
                            continue
                        }

                        // NOTE: We're intentionally NOT filtering by chatParticipantIds anymore
                        // This was causing all listings from users you've chatted with to be hidden
                        // Instead, we only want to hide specific listings you've liked or chatted about

                        val title = document.getString("title") ?: ""
                        val description = document.getString("description") ?: ""
                        val price = document.getDouble("price") ?: 0.0
                        val location = document.getString("location") ?: ""
                        val bedrooms = document.getLong("bedrooms")?.toInt() ?: 1
                        val bathrooms = document.getLong("bathrooms")?.toInt() ?: 1
                        val area = document.getLong("area")?.toInt() ?: 0

                        // Get image URLs (might be stored as an array)
                        val imagesList = document.get("imageUrls") as? List<String> ?: listOf()

                        val userName = document.getString("userName") ?: "Usuario"
                        val userProfileImageUrl = document.getString("userProfileImageUrl") ?: ""
                        val publishedDate = document.getString("publishedDate") ?: ""

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
                            publishedDate = publishedDate
                        )

                        listings.add(listing)
                        addedCount++
                        Log.d("HomeFragment", "Added listing: $id")
                    } catch (e: Exception) {
                        // Skip any malformed documents
                        Log.e("HomeFragment", "Error parsing document: ${e.message}")
                        continue
                    }
                }

                Log.d("HomeFragment", "Processed ${documents.size()} documents, added $addedCount listings. Total listings now: ${listings.size}")

                // Show the first listing if initial load, or keep current index if paginating
                if (listings.isNotEmpty()) {
                    if (isInitialLoad) {
                        currentListingIndex = 0
                        displayCurrentListing()
                    } else if (addedCount > 0) {
                        // If we're paginating and we added new listings, tell the user
                        Toast.makeText(
                            requireContext(),
                            "Se han cargado $addedCount nuevos anuncios",
                            Toast.LENGTH_SHORT
                        ).show()
                        // Continue showing the current listing
                        displayCurrentListing()
                    } else {
                        // If we didn't add any new listings after filtering
                        Toast.makeText(
                            requireContext(),
                            "No hay más anuncios disponibles",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    Log.d("HomeFragment", "No listings to display after filtering")
                    if (isInitialLoad) {
                        showNoListingsMessage()
                    } else {
                        // If pagination didn't yield any new listings
                        Toast.makeText(
                            requireContext(),
                            "No hay más anuncios disponibles",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                // Set initialLoad to false after first load
                isInitialLoad = false
                showLoading(false)
            }
            .addOnFailureListener { e ->
                Log.e("HomeFragment", "Error loading listings: ${e.message}")

                // Check if the error is related to the composite index
                if (e.message?.contains("FAILED_PRECONDITION") == true &&
                    e.message?.contains("requires an index") == true) {

                    Log.w("HomeFragment", "Missing index, falling back to simple query")

                    // Fallback to a simple query without the filter and multiple ordering
                    val fallbackQuery = db.collection("listings")
                        .orderBy("createdAt", Query.Direction.DESCENDING)
                        .limit(10)

                    fallbackQuery.get()
                        .addOnSuccessListener { documents ->
                            Log.d("HomeFragment", "Fallback query returned ${documents.size()} documents")

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

                            // Clear any previous listings if this is an initial load
                            if (isInitialLoad) {
                                listings.clear()
                            }

                            // Save the last document for pagination
                            if (documents.size() > 0) {
                                lastVisibleDocument = documents.documents[documents.size() - 1]
                            }

                            // Process documents manually (same as above but with less logging)
                            var addedCount = 0
                            for (document in documents) {
                                try {
                                    val id = document.getString("id") ?: document.id
                                    val userId = document.getString("userId") ?: ""

                                    // Skip if this listing is already liked
                                    if (likedListingIds.isNotEmpty() && id in likedListingIds) {
                                        continue
                                    }

                                    // Skip if this listing is already in a chat
                                    if (chatListingIds.isNotEmpty() && id in chatListingIds) {
                                        continue
                                    }

                                    // NOTE: We're intentionally NOT filtering by chatParticipantIds anymore

                                    val title = document.getString("title") ?: ""
                                    val description = document.getString("description") ?: ""
                                    val price = document.getDouble("price") ?: 0.0
                                    val location = document.getString("location") ?: ""
                                    val bedrooms = document.getLong("bedrooms")?.toInt() ?: 1
                                    val bathrooms = document.getLong("bathrooms")?.toInt() ?: 1
                                    val area = document.getLong("area")?.toInt() ?: 0
                                    val imagesList = document.get("imageUrls") as? List<String> ?: listOf()
                                    val userName = document.getString("userName") ?: "Usuario"
                                    val userProfileImageUrl = document.getString("userProfileImageUrl") ?: ""
                                    val publishedDate = document.getString("publishedDate") ?: ""

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
                                        publishedDate = publishedDate
                                    )

                                    listings.add(listing)
                                    addedCount++
                                } catch (e: Exception) {
                                    continue
                                }
                            }

                            Log.d("HomeFragment", "Fallback: Added $addedCount listings. Total listings now: ${listings.size}")

                            if (listings.isNotEmpty()) {
                                if (isInitialLoad) {
                                    currentListingIndex = 0
                                    displayCurrentListing()
                                } else if (addedCount > 0) {
                                    Toast.makeText(
                                        requireContext(),
                                        "Se han cargado $addedCount nuevos anuncios",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    displayCurrentListing()
                                } else {
                                    Toast.makeText(
                                        requireContext(),
                                        "No hay más anuncios disponibles",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            } else {
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
                        .addOnFailureListener { fallbackError ->
                            Log.e("HomeFragment", "Fallback query failed: ${fallbackError.message}")
                            Toast.makeText(
                                requireContext(),
                                "Error al cargar listados: ${fallbackError.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                            showLoading(false)
                            if (isInitialLoad) {
                                showNoListingsMessage()
                            }
                        }
                } else {
                    // Other error not related to index
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
    }

    private fun displayCurrentListing() {
        if (listings.isEmpty() || currentListingIndex >= listings.size) {
            showNoMoreListingsMessage()
            return
        }

        val listing = listings[currentListingIndex]

        // Show the card
        binding.cardListing.visibility = View.VISIBLE

        // Set text fields
        binding.tvTitle.text = listing.title
        binding.tvLocation.text = listing.location
        binding.tvDescription.text = listing.description
        binding.tvUserName.text = getString(R.string.publicado_por, listing.userName)
        binding.tvPublishedDate.text = getString(R.string.publicado_fecha, listing.publishedDate)

        // Format price
        val formattedPrice = NumberFormat.getCurrencyInstance(Locale("es", "ES"))
            .format(listing.price)
        binding.tvPrice.text = getString(R.string.precio_por_mes, formattedPrice)

        // Set details
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

        // Load user profile image
        Glide.with(requireContext())
            .load(listing.userProfileImageUrl)
            .placeholder(R.drawable.default_profile_img)
            .error(R.drawable.default_profile_img)
            .into(binding.ivUserProfile)

        // Setup image carousel
        setupImageCarousel(listing.imageUrls)
    }

    private fun setupImageCarousel(imageUrls: List<String>) {
        // Create adapter
        listingImageAdapter = ListingImageAdapter(imageUrls)
        binding.viewPagerImages.adapter = listingImageAdapter

        // Setup dots indicator
        setupDotsIndicator(imageUrls.size)

        // Handle page changes
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

        // Create indicator dots
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
        val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser ?: return

        // Save to "likes" collection
        val likeData = hashMapOf(
            "userId" to currentUser.uid,
            "listingId" to listingId,
            "timestamp" to com.google.firebase.Timestamp.now()
        )

        db.collection("likes")
            .add(likeData)
            .addOnSuccessListener {
                // Success, handled in showContactDialog()
            }
            .addOnFailureListener { e ->
                Toast.makeText(
                    requireContext(),
                    "Error al guardar like: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun showNoMoreListingsMessage() {
        binding.cardListing.visibility = View.INVISIBLE
        Toast.makeText(
            requireContext(),
            getString(R.string.no_listings_available),
            Toast.LENGTH_LONG
        ).show()

        // Optional: fetch more listings
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

    private fun loadMoreListings() {
        // If we're already loading, don't trigger another load
        if (binding.progressBar.visibility == View.VISIBLE) {
            return
        }

        Toast.makeText(requireContext(), "Buscando más propiedades...", Toast.LENGTH_SHORT).show()

        // Set isInitialLoad to false since we're paginating
        isInitialLoad = false

        // Show loading indicator
        showLoading(true)

        // Reload listings with pagination
        loadListingsFromFirebase()
    }

    private fun showLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnAccept.isEnabled = !isLoading
        binding.btnReject.isEnabled = !isLoading
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}