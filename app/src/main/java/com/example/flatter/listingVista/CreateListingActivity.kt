package com.example.flatter.listingVista

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.flatter.R
import com.example.flatter.databinding.ActivityCreateListingBinding
import com.example.flatter.homeVista.HomeActivity
import com.example.flatter.utils.FlatterToast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class CreateListingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreateListingBinding
    private var selectedImages = mutableListOf<Uri>()
    private val imageUrls = mutableListOf<String>()
    private lateinit var imageAdapter: CreateListingImageAdapter

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()

    //image picker
    private val getImages = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            selectedImages.addAll(uris)
            imageAdapter.notifyDataSetChanged()
            updateImageCountLabel()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreateListingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        setupDistrictSpinner()
        setupListeners()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Crear anuncio"
        binding.toolbar.setNavigationOnClickListener { onBackPressed() }
    }

    private fun setupRecyclerView() {
        imageAdapter = CreateListingImageAdapter(selectedImages) { position ->
            selectedImages.removeAt(position)
            imageAdapter.notifyItemRemoved(position)
            updateImageCountLabel()
        }
        binding.recyclerViewImages.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.recyclerViewImages.adapter = imageAdapter
    }

    private fun setupDistrictSpinner() {
        val districts = arrayOf(
            "Arganzuela", "Barajas", "Carabanchel", "Centro", "Chamartín",
            "Chamberí", "Ciudad Lineal", "Fuencarral-El Pardo", "Hortaleza",
            "Latina", "Moncloa-Aravaca", "Moratalaz", "Puente de Vallecas",
            "Retiro", "Salamanca", "San Blas", "Tetuán", "Usera",
            "Vicálvaro", "Villa de Vallecas", "Villaverde"
        )

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, districts)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerDistrict.adapter = adapter
    }

    private fun setupListeners() {
        binding.btnAddPhotos.setOnClickListener {
            getImages.launch("image/*")
        }

        binding.btnPublish.setOnClickListener {
            if (validateForm()) {
                publishListing()
            }
        }
    }

    private fun updateImageCountLabel() {
        binding.tvSelectedImagesCount.text = "${selectedImages.size} ${getString(R.string.photos_selected)}"
        binding.recyclerViewImages.visibility = if (selectedImages.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun validateForm(): Boolean {
        var isValid = true

        //validate title
        if (binding.etTitle.text.toString().trim().isEmpty()) {
            binding.etTitle.error = "El título es obligatorio"
            isValid = false
        }

        //validate price
        if (binding.etPrice.text.toString().trim().isEmpty()) {
            binding.etPrice.error = "El precio es obligatorio"
            isValid = false
        }

        //validate description
        if (binding.etDescription.text.toString().trim().isEmpty()) {
            binding.etDescription.error = "La descripción es obligatoria"
            isValid = false
        }

        //validate images
        if (selectedImages.isEmpty()) {
            FlatterToast.showError(this, "Añade al menos una foto")
            isValid = false
        }

        return isValid
    }

    private fun publishListing() {
        binding.progressBar.visibility = View.VISIBLE
        binding.btnPublish.isEnabled = false

        val userId = auth.currentUser?.uid
        if (userId == null) {
            FlatterToast.showError(this, "Debes iniciar sesión para publicar")
            binding.progressBar.visibility = View.GONE
            binding.btnPublish.isEnabled = true
            return
        }

        uploadImagesToStorage(userId) { imageUrls ->
            createListingInFirestore(userId, imageUrls)
        }
    }

    private fun uploadImagesToStorage(userId: String, onComplete: (List<String>) -> Unit) {
        if (selectedImages.isEmpty()) {
            onComplete(emptyList())
            return
        }

        val uploadedUrls = mutableListOf<String>()
        var uploadCount = 0

        selectedImages.forEach { uri ->
            val imageName = "listings/${userId}/${UUID.randomUUID()}.jpg"
            val imageRef = storage.reference.child(imageName)

            imageRef.putFile(uri)
                .addOnSuccessListener {
                    imageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                        uploadedUrls.add(downloadUri.toString())
                        uploadCount++

                        if (uploadCount == selectedImages.size) {
                            onComplete(uploadedUrls)
                        }
                    }
                }
                .addOnFailureListener { e ->
                    FlatterToast.showError(this, "Error al subir imagen: ${e.message}")
                    binding.progressBar.visibility = View.GONE
                    binding.btnPublish.isEnabled = true
                }
        }
    }

    private fun createListingInFirestore(userId: String, imageUrls: List<String>) {
        val title = binding.etTitle.text.toString().trim()
        val description = binding.etDescription.text.toString().trim()
        val district = binding.spinnerDistrict.selectedItem.toString()
        val price = binding.etPrice.text.toString().toDoubleOrNull() ?: 0.0
        val bedrooms = binding.etBedrooms.text.toString().toIntOrNull() ?: 1
        val bathrooms = binding.etBathrooms.text.toString().toIntOrNull() ?: 1
        val area = binding.etArea.text.toString().toIntOrNull() ?: 0

        val propertyType = when {
            binding.radioApartment.isChecked -> "Apartamento"
            binding.radioRoom.isChecked -> "Habitación"
            binding.radioHouse.isChecked -> "Casa"
            else -> "Apartamento"
        }

        //get user info including user typ
        db.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                val userName = document.getString("fullName") ?: "Usuario"
                val userProfileImageUrl = document.getString("profileImageUrl") ?: ""
                val userType = document.getString("userType") ?: "propietario"

                //d log to verify user type retrieval
                Log.d("CreateListingActivity", "Creating listing for user: $userName with userType: '$userType'")

                //create listing object
                val listingId = UUID.randomUUID().toString()
                val formatoFecha = SimpleDateFormat("dd/MM/yyyy", Locale("es", "ES"))
                val publishedDate = formatoFecha.format(Date())

                val location = "$district, Madrid"

                val listing = hashMapOf(
                    "id" to listingId,
                    "title" to title,
                    "description" to description,
                    "price" to price,
                    "location" to location,
                    "bedrooms" to bedrooms,
                    "bathrooms" to bathrooms,
                    "area" to area,
                    "imageUrls" to imageUrls,
                    "userId" to userId,
                    "userName" to userName,
                    "userProfileImageUrl" to userProfileImageUrl,
                    "userType" to userType, //Always include user type in listing
                    "publishedDate" to publishedDate,
                    "propertyType" to propertyType,
                    "createdAt" to Date(),
                    "status" to "active"
                )

                //debug log the complete listing object
                Log.d("CreateListingActivity", "Complete listing object: $listing")

                //save to Firestore
                db.collection("listings").document(listingId)
                    .set(listing)
                    .addOnSuccessListener {
                        //verify the listing was saved with user type
                        db.collection("listings").document(listingId).get()
                            .addOnSuccessListener { savedDoc ->
                                val savedUserType = savedDoc.getString("userType")
                                Log.d("CreateListingActivity", "Verification - Listing saved with userType: '$savedUserType'")
                            }

                        FlatterToast.showSuccess(this, "¡Anuncio publicado con éxito!")

                        //return to home activity
                        val intent = Intent(this, HomeActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                        startActivity(intent)
                        finish()
                    }
                    .addOnFailureListener { e ->
                        FlatterToast.showError(this, "Error: ${e.message}")
                        binding.progressBar.visibility = View.GONE
                        binding.btnPublish.isEnabled = true
                    }
            }
            .addOnFailureListener { e ->
                FlatterToast.showError(this, "Error al obtener información del usuario: ${e.message}")
                Log.e("CreateListingActivity", "Error fetching user info: ${e.message}")
                binding.progressBar.visibility = View.GONE
                binding.btnPublish.isEnabled = true
            }
    }
}
