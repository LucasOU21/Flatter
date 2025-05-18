package com.example.flatter.listingVista

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.example.flatter.R
import com.example.flatter.databinding.FragmentListingManagerBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ListingManagerFragment : Fragment() {

    private var _binding: FragmentListingManagerBinding? = null
    private val binding get() = _binding!!

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val userId = auth.currentUser?.uid

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentListingManagerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Set up click listeners for the options
        setupClickListeners()
    }

    private fun setupClickListeners() {
        // Create listing option
        binding.layoutCreateListing.setOnClickListener {
            checkListingLimit()
        }

        // Analytics option
        binding.layoutAnalytics.setOnClickListener {
            showListingAnalytics()
        }

        // Delete option
        binding.layoutDelete.setOnClickListener {
            showDeleteOptions()
        }
    }

    // Check if the user has reached the limit of 3 listings
    private fun checkListingLimit() {
        if (userId == null) {
            Toast.makeText(context, "Debes iniciar sesión para crear anuncios", Toast.LENGTH_SHORT).show()
            return
        }

        binding.progressBar.visibility = View.VISIBLE

        db.collection("listings")
            .whereEqualTo("userId", userId)
            .whereEqualTo("status", "active")
            .get()
            .addOnSuccessListener { documents ->
                binding.progressBar.visibility = View.GONE
                val activeListingsCount = documents.size()

                if (activeListingsCount >= 3) {
                    // User has reached the limit
                    showLimitReachedDialog()
                } else {
                    // User can create a new listing
                    startActivity(Intent(requireContext(), CreateListingActivity::class.java))
                }
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // Show a dialog when the user has reached the listing limit
    private fun showLimitReachedDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Límite alcanzado")
            .setMessage("Has alcanzado el límite de 3 anuncios activos. Para crear uno nuevo, debes eliminar o desactivar un anuncio existente.")
            .setPositiveButton("Entendido") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    // Show the analytics screen for the user's listings
    private fun showListingAnalytics() {
        if (userId == null) {
            Toast.makeText(context, "Debes iniciar sesión para ver estadísticas", Toast.LENGTH_SHORT).show()
            return
        }

        startActivity(Intent(requireContext(), ListingAnalyticsActivity::class.java))
    }

    // Show the delete options screen
    private fun showDeleteOptions() {
        if (userId == null) {
            Toast.makeText(context, "Debes iniciar sesión para eliminar anuncios", Toast.LENGTH_SHORT).show()
            return
        }

        startActivity(Intent(requireContext(), DeleteListingsActivity::class.java))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}