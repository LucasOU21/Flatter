package com.example.flatter.listingVista

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.example.flatter.databinding.ActivityListingAnalyticsBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ListingAnalyticsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityListingAnalyticsBinding
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val userId = auth.currentUser?.uid

    private lateinit var pagerAdapter: ListingAnalyticsPagerAdapter
    private var listings = mutableListOf<ListingAnalyticsModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityListingAnalyticsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Set up toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Estadísticas de anuncios"
        binding.toolbar.setNavigationOnClickListener { onBackPressed() }

        // Set up ViewPager
        setupViewPager()

        // Set up navigation buttons
        setupPageNavigation()

        // Load user's listings
        loadUserListings()
    }

    private fun setupViewPager() {
        // Initialize the adapter with empty list (will be updated when data loads)
        pagerAdapter = ListingAnalyticsPagerAdapter(
            listings,
            onStatusChange = { listingId, newStatus ->
                updateListingStatus(listingId, newStatus)
            }
        )

        binding.viewPagerListings.adapter = pagerAdapter

        // Add page change callback to update the UI
        binding.viewPagerListings.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updatePageInfo(position)
                updateNavigationButtons(position)
            }
        })
    }

    private fun setupPageNavigation() {
        binding.btnPrevious.setOnClickListener {
            val currentPosition = binding.viewPagerListings.currentItem
            if (currentPosition > 0) {
                binding.viewPagerListings.currentItem = currentPosition - 1
            }
        }

        binding.btnNext.setOnClickListener {
            val currentPosition = binding.viewPagerListings.currentItem
            if (currentPosition < listings.size - 1) {
                binding.viewPagerListings.currentItem = currentPosition + 1
            }
        }
    }

    private fun updatePageInfo(position: Int) {
        binding.tvPageInfo.text = "Anuncio ${position + 1} de ${listings.size}"
    }

    private fun updateNavigationButtons(position: Int) {
        binding.btnPrevious.isEnabled = position > 0
        binding.btnNext.isEnabled = position < listings.size - 1
    }

    private fun loadUserListings() {
        binding.progressBar.visibility = View.VISIBLE

        db.collection("listings")
            .whereEqualTo("userId", userId)
            .get()
            .addOnSuccessListener { documents ->
                binding.progressBar.visibility = View.GONE

                if (documents.isEmpty) {
                    showNoListingsMessage()
                    return@addOnSuccessListener
                }

                listings.clear()
                listings.addAll(documents.mapNotNull { document ->
                    try {
                        val id = document.id
                        val title = document.getString("title") ?: ""
                        val imageUrl = document.get("imageUrls") as? List<String>
                        val firstImage = if (imageUrl?.isNotEmpty() == true) imageUrl[0] else ""
                        val status = document.getString("status") ?: "active"

                        // Get analytics data
                        val likes = document.getLong("likeCount")?.toInt() ?: 0
                        val views = document.getLong("viewCount")?.toInt() ?: 0
                        val chatCount = document.getLong("chatCount")?.toInt() ?: 0

                        ListingAnalyticsModel(id, title, firstImage, status, likes, views, chatCount)
                    } catch (e: Exception) {
                        null
                    }
                })

                if (listings.isNotEmpty()) {
                    pagerAdapter.notifyDataSetChanged()
                    updatePageInfo(0)
                    updateNavigationButtons(0)
                    binding.viewPagerListings.visibility = View.VISIBLE
                    binding.layoutPagination.visibility = View.VISIBLE
                } else {
                    showNoListingsMessage()
                }
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                binding.tvError.text = "Error: ${e.message}"
                binding.tvError.visibility = View.VISIBLE
            }
    }

    private fun updateListingStatus(listingId: String, newStatus: String) {
        db.collection("listings").document(listingId)
            .update("status", newStatus)
            .addOnFailureListener { e ->
                binding.tvError.text = "Error al actualizar estado: ${e.message}"
                binding.tvError.visibility = View.VISIBLE
            }
    }

    private fun showNoListingsMessage() {
        binding.tvNoListings.visibility = View.VISIBLE
        binding.viewPagerListings.visibility = View.GONE
        binding.layoutPagination.visibility = View.GONE
    }
}