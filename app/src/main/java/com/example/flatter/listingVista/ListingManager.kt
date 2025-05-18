package com.example.flatter.listingVista

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import com.example.flatter.R
import com.example.flatter.databinding.DialogListingOptionsBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ListingManager(private val context: Context) {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val userId = auth.currentUser?.uid

    // Show the options dialog
    fun showListingOptionsDialog() {
        val dialogBinding = DialogListingOptionsBinding.inflate(LayoutInflater.from(context))
        val dialog = AlertDialog.Builder(context)
            .setView(dialogBinding.root)
            .create()

        // Create listing option
        dialogBinding.layoutCreateListing.setOnClickListener {
            dialog.dismiss()
            checkListingLimit()
        }

        // Analytics option
        dialogBinding.layoutAnalytics.setOnClickListener {
            dialog.dismiss()
            showListingAnalytics()
        }

        // Delete option
        dialogBinding.layoutDelete.setOnClickListener {
            dialog.dismiss()
            showDeleteOptions()
        }

        dialog.show()
    }

    // Check if the user has reached the limit of 3 listings
    private fun checkListingLimit() {
        if (userId == null) {
            Toast.makeText(context, "Debes iniciar sesión para crear anuncios", Toast.LENGTH_SHORT).show()
            return
        }

        db.collection("listings")
            .whereEqualTo("userId", userId)
            .whereEqualTo("status", "active")
            .get()
            .addOnSuccessListener { documents ->
                val activeListingsCount = documents.size()

                if (activeListingsCount >= 3) {
                    // User has reached the limit
                    showLimitReachedDialog()
                } else {
                    // User can create a new listing
                    context.startActivity(Intent(context, CreateListingActivity::class.java))
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // Show a dialog when the user has reached the listing limit
    private fun showLimitReachedDialog() {
        AlertDialog.Builder(context)
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

        context.startActivity(Intent(context, ListingAnalyticsActivity::class.java))
    }

    // Show the delete options screen
    private fun showDeleteOptions() {
        if (userId == null) {
            Toast.makeText(context, "Debes iniciar sesión para eliminar anuncios", Toast.LENGTH_SHORT).show()
            return
        }

        context.startActivity(Intent(context, DeleteListingsActivity::class.java))
    }
}