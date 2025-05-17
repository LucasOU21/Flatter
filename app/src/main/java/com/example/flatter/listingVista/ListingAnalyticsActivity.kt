package com.example.flatter.listingVista

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.flatter.databinding.ActivityListingAnalyticsBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ListingAnalyticsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityListingAnalyticsBinding
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val userId = auth.currentUser?.uid

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityListingAnalyticsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Set up toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Estadísticas de anuncios"
        binding.toolbar.setNavigationOnClickListener { onBackPressed() }

        // Set up RecyclerView
        binding.recyclerViewListings.layoutManager = LinearLayoutManager(this)

        // Load user's listings
        loadUserListings()
    }

    private fun loadUserListings() {
        binding.progressBar.visibility = View.VISIBLE

        db.collection("listings")
            .whereEqualTo("userId", userId)
            .get()
            .addOnSuccessListener { documents ->
                binding.progressBar.visibility = View.GONE

                if (documents.isEmpty) {
                    binding.tvNoListings.visibility = View.VISIBLE
                    binding.recyclerViewListings.visibility = View.GONE
                    return@addOnSuccessListener
                }

                val listings = documents.mapNotNull { document ->
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
                }

                // Set up adapter
                val adapter = ListingAnalyticsAdapter(
                    listings,
                    onStatusChange = { listingId, newStatus ->
                        updateListingStatus(listingId, newStatus)
                    }
                )
                binding.recyclerViewListings.adapter = adapter

                binding.tvNoListings.visibility = View.GONE
                binding.recyclerViewListings.visibility = View.VISIBLE
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                binding.tvError.text = "Error: ${e.message}"
                binding.tvError.visibility = View.VISIBLE
            }
    }

    private fun updateListingStatus(listingId: String, newStatus: String) {
        binding.progressBar.visibility = View.VISIBLE

        db.collection("listings").document(listingId)
            .update("status", newStatus)
            .addOnSuccessListener {
                binding.progressBar.visibility = View.GONE
                // Reload the listings to reflect the change
                loadUserListings()
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                binding.tvError.text = "Error al actualizar estado: ${e.message}"
                binding.tvError.visibility = View.VISIBLE
            }
    }
}