package com.example.flatter.homeVista

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.example.flatter.R
import com.example.flatter.databinding.FragmentHomeBinding
import com.example.flatter.listingVista.ListingManager
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.NumberFormat
import java.util.Locale

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var listingImageAdapter: ListingImageAdapter
    private var currentListingIndex = 0
    private var listings = mutableListOf<ListingModel>()
    private val db = FirebaseFirestore.getInstance()

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

                // Save this listing as "liked" in Firestore
                saveLikedListing(currentListing.id)

                // Show match message
                Toast.makeText(
                    requireContext(),
                    "¡Coincidencia! Ponte en contacto con ${currentListing.userName}",
                    Toast.LENGTH_SHORT
                ).show()

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
    }

    private fun loadListingsFromFirebase() {
        showLoading(true)

        // Clear existing listings
        listings.clear()

        // Query Firestore for listings
        db.collection("listings")
            .orderBy("createdAt", Query.Direction.DESCENDING) // Most recent first
            .limit(20) // Limit to 20 listings for performance
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    showNoListingsMessage()
                    return@addOnSuccessListener
                }

                // Parse documents into ListingModel objects
                for (document in documents) {
                    try {
                        val id = document.getString("id") ?: document.id
                        val title = document.getString("title") ?: ""
                        val description = document.getString("description") ?: ""
                        val price = document.getDouble("price") ?: 0.0
                        val location = document.getString("location") ?: ""
                        val bedrooms = document.getLong("bedrooms")?.toInt() ?: 1
                        val bathrooms = document.getLong("bathrooms")?.toInt() ?: 1
                        val area = document.getLong("area")?.toInt() ?: 0

                        // Get image URLs (might be stored as an array)
                        val imagesList = document.get("imageUrls") as? List<String> ?: listOf()

                        val userId = document.getString("userId") ?: ""
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
                    } catch (e: Exception) {
                        // Skip any malformed documents
                        continue
                    }
                }

                // Show the first listing
                if (listings.isNotEmpty()) {
                    currentListingIndex = 0
                    displayCurrentListing()
                } else {
                    showNoListingsMessage()
                }

                showLoading(false)
            }
            .addOnFailureListener { e ->
                Toast.makeText(
                    requireContext(),
                    "Error al cargar listados: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
                showLoading(false)
                showNoListingsMessage()
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
                // Success, already handled in UI
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
        // Here you could implement pagination to load more listings
        // For now we'll just show a message
        Toast.makeText(requireContext(), "Buscando más propiedades...", Toast.LENGTH_SHORT).show()

        // TODO: Implement pagination logic
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