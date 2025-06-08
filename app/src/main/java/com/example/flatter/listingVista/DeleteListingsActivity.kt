package com.example.flatter.listingVista

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.flatter.databinding.ActivityDeleteListingsBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class DeleteListingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDeleteListingsBinding
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val userId = auth.currentUser?.uid

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDeleteListingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        //set up toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Eliminar anuncios"
        binding.toolbar.setNavigationOnClickListener { onBackPressed() }

        //set up RecyclerView
        binding.recyclerViewListings.layoutManager = LinearLayoutManager(this)

        //load user's listings
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

                        DeleteListingModel(id, title, firstImage)
                    } catch (e: Exception) {
                        null
                    }
                }

                //set up adapter
                val adapter = DeleteListingsAdapter(
                    listings,
                    onDeleteClick = { listingId ->
                        confirmDeletion(listingId)
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

    private fun confirmDeletion(listingId: String) {
        AlertDialog.Builder(this)
            .setTitle("Confirmar eliminación")
            .setMessage("¿Estás seguro de que quieres eliminar este anuncio? Esta acción no se puede deshacer.")
            .setPositiveButton("Eliminar") { _, _ ->
                deleteListing(listingId)
            }
            .setNegativeButton("Cancelar") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun deleteListing(listingId: String) {
        binding.progressBar.visibility = View.VISIBLE

        db.collection("listings").document(listingId)
            .delete()
            .addOnSuccessListener {
                binding.progressBar.visibility = View.GONE
                //show success message
                AlertDialog.Builder(this)
                    .setTitle("Anuncio eliminado")
                    .setMessage("El anuncio ha sido eliminado correctamente.")
                    .setPositiveButton("Aceptar") { dialog, _ ->
                        dialog.dismiss()
                        //  the listings to reflect the change
                        loadUserListings()
                    }
                    .show()
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                binding.tvError.text = "Error al eliminar: ${e.message}"
                binding.tvError.visibility = View.VISIBLE
            }
    }
}

data class DeleteListingModel(
    val id: String,
    val title: String,
    val imageUrl: String
)