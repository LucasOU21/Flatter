package com.example.flatter

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.example.flatter.databinding.FragmentProfileBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var storage: FirebaseStorage

    private var profileImageUri: Uri? = null

    //Register image picker activity with improved implementation
    private val pickImage = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                try {
                    //Update the displayed image immediately
                    profileImageUri = uri

                    //Use Glide for better image loading and error handling
                    Glide.with(requireContext())
                        .load(uri)
                        .placeholder(R.drawable.default_profile_img)
                        .error(R.drawable.default_profile_img)
                        .into(binding.ivProfilePicture)

                    //The actual upload happens when the user clicks Save
                    Toast.makeText(requireContext(), "Image selected, click Save to update your profile", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Error displaying image: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        //Initialize Firebase
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()

        // Configure UI listeners
        setupListeners()

        // Load user profile data
        loadUserProfile()
    }

    private fun setupListeners() {
        // Image edit listener
        binding.tvEditPhoto.setOnClickListener {
            try {
                // Create the gallery intent
                val galleryIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)

                // Make sure the device can handle this intent
                if (galleryIntent.resolveActivity(requireActivity().packageManager) != null) {
                    pickImage.launch(galleryIntent)
                } else {
                    Toast.makeText(requireContext(), "No gallery app found", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error opening gallery: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        // Save button listener
        binding.btnSaveProfile.setOnClickListener {
            saveUserProfile()
        }

        // Add sign out button functionality
        binding.btnSignOut?.setOnClickListener {
            signOut()
        }
    }

    private fun loadUserProfile() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            // Not logged in, redirect to login
            navigateToLogin()
            return
        }

        // Show loading state
        showLoading(true)

        // Get user data from Firestore
        db.collection("users").document(currentUser.uid)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    // Populate fields with user data
                    binding.etFullName.setText(document.getString("fullName") ?: "")
                    binding.etEmail.setText(document.getString("email") ?: "")
                    binding.etPhone.setText(document.getString("phone") ?: "")
                    binding.etBio.setText(document.getString("bio") ?: "")

                    // Budget might be stored as a number
                    val budget = document.getDouble("maxBudget")?.toString() ?: ""
                    binding.etMaxBudget.setText(budget)

                    // Load profile image if available
                    val profileImageUrl = document.getString("profileImageUrl")
                    if (!profileImageUrl.isNullOrEmpty()) {
                        Glide.with(requireContext())
                            .load(profileImageUrl)
                            .placeholder(R.drawable.default_profile_img)
                            .error(R.drawable.default_profile_img)
                            .into(binding.ivProfilePicture)
                    }
                } else {
                    // Document doesn't exist, create it
                    createNewUserProfile(currentUser.uid, currentUser.email ?: "")
                }
                showLoading(false)
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Error al cargar perfil: ${e.message}", Toast.LENGTH_SHORT).show()
                showLoading(false)
            }
    }

    private fun createNewUserProfile(userId: String, email: String) {
        val userData = hashMapOf(
            "email" to email,
            "fullName" to "",
            "phone" to "",
            "bio" to "",
            "maxBudget" to 0.0,
            "profileImageUrl" to "",
            "createdAt" to com.google.firebase.Timestamp.now()
        )

        db.collection("users").document(userId)
            .set(userData)
            .addOnSuccessListener {
                // Profile created successfully, fields already empty
                binding.etEmail.setText(email)
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Error al crear perfil: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun saveUserProfile() {
        val currentUser = auth.currentUser ?: return

        // Validate form
        val fullName = binding.etFullName.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val phone = binding.etPhone.text.toString().trim()
        val bio = binding.etBio.text.toString().trim()
        val maxBudgetStr = binding.etMaxBudget.text.toString().trim()

        if (fullName.isEmpty()) {
            binding.etFullName.error = "Campo obligatorio"
            return
        }

        if (email.isEmpty()) {
            binding.etEmail.error = "Campo obligatorio"
            return
        }

        // Show loading
        showLoading(true)

        // Handle profile image upload if changed
        if (profileImageUri != null) {
            uploadProfileImage(currentUser.uid) { imageUrl ->
                saveUserDataToFirestore(currentUser.uid, fullName, email, phone, bio, maxBudgetStr, imageUrl)
            }
        } else {
            // No new image, just update the text fields
            saveUserDataToFirestore(currentUser.uid, fullName, email, phone, bio, maxBudgetStr, null)
        }
    }

    private fun uploadProfileImage(userId: String, onSuccess: (String) -> Unit) {
        val imageRef = storage.reference.child("profile_images/$userId.jpg")

        profileImageUri?.let { uri ->
            imageRef.putFile(uri)
                .addOnSuccessListener {
                    // Get download URL
                    imageRef.downloadUrl
                        .addOnSuccessListener { downloadUri ->
                            onSuccess(downloadUri.toString())
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(requireContext(), "Error al obtener URL de imagen: ${e.message}", Toast.LENGTH_SHORT).show()
                            showLoading(false)
                        }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(requireContext(), "Error al subir imagen: ${e.message}", Toast.LENGTH_SHORT).show()
                    showLoading(false)
                }
        }
    }

    private fun saveUserDataToFirestore(
        userId: String,
        fullName: String,
        email: String,
        phone: String,
        bio: String,
        maxBudgetStr: String,
        imageUrl: String?
    ) {
        // Convert budget to double or 0.0 if empty
        val maxBudget = maxBudgetStr.toDoubleOrNull() ?: 0.0

        // Create update map
        val userUpdates = hashMapOf(
            "fullName" to fullName,
            "email" to email,
            "phone" to phone,
            "bio" to bio,
            "maxBudget" to maxBudget,
            "updatedAt" to com.google.firebase.Timestamp.now()
        )

        // Add image URL if provided
        if (imageUrl != null) {
            userUpdates["profileImageUrl"] = imageUrl
        }

        // Update Firestore
        db.collection("users").document(userId)
            .update(userUpdates as Map<String, Any>)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Perfil actualizado correctamente", Toast.LENGTH_SHORT).show()
                showLoading(false)
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Error al actualizar perfil: ${e.message}", Toast.LENGTH_SHORT).show()
                showLoading(false)
            }
    }

    private fun signOut() {
        auth.signOut()
        navigateToLogin()
    }

    private fun navigateToLogin() {
        // Import the correct LoginActivity class based on your package structure
        val intent = Intent(requireContext(), com.example.flatter.loginVista.LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        requireActivity().finish()
    }

    private fun showLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnSaveProfile.isEnabled = !isLoading
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}