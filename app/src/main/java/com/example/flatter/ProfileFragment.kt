package com.example.flatter

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.example.flatter.databinding.FragmentProfileBinding
import com.example.flatter.loginVista.LoginActivity
import com.example.flatter.utils.FlatterToast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    // This property is only valid between onCreateView and onDestroyView
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var storage: FirebaseStorage

    private var profileImageUri: Uri? = null

    // Register image picker activity with improved implementation
    private val pickImage = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                try {
                    // Update the displayed image immediately
                    profileImageUri = uri

                    // Use Glide for better image loading and error handling
                    Glide.with(requireContext())
                        .load(uri)
                        .placeholder(R.drawable.default_profile_img)
                        .error(R.drawable.default_profile_img)
                        .into(binding.ivProfilePicture)

                    // The actual upload happens when the user clicks Save
                    FlatterToast.showShort(requireContext(), "Imagen seleccionada, haz clic en Guardar para actualizar tu perfil")
                } catch (e: Exception) {
                    FlatterToast.showError(requireContext(), "Error al mostrar imagen: ${e.message}")
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

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()

        // Configure UI listeners
        setupListeners()

        // Load user profile data
        loadUserProfile()
    }

    private fun setupListeners() {
        // Image edit button or area
        binding.fabEditPhoto.setOnClickListener {
            openImagePicker()
        }

        // Save button listener
        binding.btnSaveProfile.setOnClickListener {
            saveUserProfile()
        }

        // Add sign out button functionality
        binding.btnSignOut.setOnClickListener {
            signOut()
        }
    }

    private fun openImagePicker() {
        try {
            // Create the gallery intent
            val galleryIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)

            // Make sure the device can handle this intent
            if (galleryIntent.resolveActivity(requireActivity().packageManager) != null) {
                pickImage.launch(galleryIntent)
            } else {
                FlatterToast.showError(requireContext(), "No se encontró una aplicación de galería")
            }
        } catch (e: Exception) {
            FlatterToast.showError(requireContext(), "Error al abrir galería: ${e.message}")
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
                // Check if the fragment is still attached to avoid NPE
                if (!isAdded || _binding == null) return@addOnSuccessListener

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
                // Check if the fragment is still attached to avoid NPE
                if (!isAdded || _binding == null) return@addOnFailureListener

                FlatterToast.showError(requireContext(), "Error al cargar perfil: ${e.message}")
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
                // Check if the fragment is still attached to avoid NPE
                if (!isAdded || _binding == null) return@addOnSuccessListener

                // Profile created successfully, fields already empty
                binding.etEmail.setText(email)
            }
            .addOnFailureListener { e ->
                // Check if the fragment is still attached to avoid NPE
                if (!isAdded || _binding == null) return@addOnFailureListener

                FlatterToast.showError(requireContext(), "Error al crear perfil: ${e.message}")
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
        // Create a reference to the user's profile image in Firebase Storage
        val imageRef = storage.reference.child("profile_images/$userId.jpg")

        profileImageUri?.let { uri ->
            // Check if the fragment is still attached to avoid NPE
            if (!isAdded || _binding == null) return@let

            // Show progress indicator
            binding.progressBar.visibility = View.VISIBLE

            // Upload the image to Firebase Storage
            imageRef.putFile(uri)
                .addOnSuccessListener {
                    // Get download URL after successful upload
                    imageRef.downloadUrl
                        .addOnSuccessListener { downloadUri ->
                            // Call the onSuccess callback with the download URL
                            onSuccess(downloadUri.toString())
                        }
                        .addOnFailureListener { e ->
                            // Check if the fragment is still attached to avoid NPE
                            if (!isAdded || _binding == null) return@addOnFailureListener

                            showLoading(false)
                            FlatterToast.showError(requireContext(), "Error al obtener URL de imagen: ${e.message}")
                        }
                }
                .addOnFailureListener { e ->
                    // Check if the fragment is still attached to avoid NPE
                    if (!isAdded || _binding == null) return@addOnFailureListener

                    showLoading(false)
                    FlatterToast.showError(requireContext(), "Error al subir imagen: ${e.message}")
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
        // Check if the fragment is still attached to avoid NPE
        if (!isAdded || _binding == null) return

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
                // Check if the fragment is still attached to avoid NPE
                if (!isAdded || _binding == null) return@addOnSuccessListener

                FlatterToast.showSuccess(requireContext(), "Perfil actualizado correctamente")

                // Reset the profile image URI since we've processed it
                profileImageUri = null

                showLoading(false)
            }
            .addOnFailureListener { e ->
                // Check if the fragment is still attached to avoid NPE
                if (!isAdded || _binding == null) return@addOnFailureListener

                FlatterToast.showError(requireContext(), "Error al actualizar perfil: ${e.message}")
                showLoading(false)
            }
    }

    private fun signOut() {
        auth.signOut()
        navigateToLogin()
    }

    private fun navigateToLogin() {
        // Import the correct LoginActivity class based on your package structure
        val intent = Intent(requireContext(), LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        requireActivity().finish()
    }

    private fun showLoading(isLoading: Boolean) {
        // Check if the fragment is still attached to avoid NPE
        if (!isAdded || _binding == null) return

        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnSaveProfile.isEnabled = !isLoading
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Important: clear the binding reference to avoid memory leaks
        _binding = null
    }
}