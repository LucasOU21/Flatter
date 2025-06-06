package com.example.flatter.registerVista

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.util.Patterns
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.example.flatter.PasswordStrengthMeter
import com.example.flatter.homeVista.HomeActivity
import com.example.flatter.R
import com.example.flatter.databinding.ActivityRegisterBinding
import com.example.flatter.utils.FlatterToast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private val TAG = "RegisterActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Inicializar Firebase Auth y Firestore
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Setup password strength meter
        setupPasswordStrengthMeter()

        // Configurar botón de registro
        binding.btnRegister.setOnClickListener {
            if (validateForm()) {
                registerUser()
            }
        }

        // Volver a la pantalla de login
        binding.tvLogin.setOnClickListener {
            finish() // Regresa a la actividad anterior (Login)
        }

        // Aplicar animaciones a los campos al enfocarlos
        setupFocusAnimations()
    }

    private fun setupFocusAnimations() {
        val scaleUp = android.animation.AnimatorSet().apply {
            play(android.animation.ObjectAnimator.ofFloat(binding.imgLogo, "scaleX", 1.1f))
                .with(android.animation.ObjectAnimator.ofFloat(binding.imgLogo, "scaleY", 1.1f))
            duration = 300
        }

        val scaleDown = android.animation.AnimatorSet().apply {
            play(android.animation.ObjectAnimator.ofFloat(binding.imgLogo, "scaleX", 1f))
                .with(android.animation.ObjectAnimator.ofFloat(binding.imgLogo, "scaleY", 1f))
            duration = 300
        }

        // Aplicar animación cuando algún campo recibe el foco
        val focusListener = View.OnFocusChangeListener { _, hasFocus ->
            if (hasFocus) scaleUp.start() else scaleDown.start()
        }

        binding.etUsername.onFocusChangeListener = focusListener
        binding.etEmail.onFocusChangeListener = focusListener
        binding.etPhone.onFocusChangeListener = focusListener
        binding.etPassword.onFocusChangeListener = focusListener
        binding.etConfirmPassword.onFocusChangeListener = focusListener
    }

    private fun validateForm(): Boolean {
        var valid = true

        // Validar nombre de usuario
        val username = binding.etUsername.text.toString().trim()
        if (username.isEmpty()) {
            binding.tilUsername.error = getString(R.string.required_field)
            valid = false
        } else {
            binding.tilUsername.error = null
        }

        // Validar email
        val email = binding.etEmail.text.toString().trim()
        if (email.isEmpty()) {
            binding.tilEmail.error = getString(R.string.required_field)
            valid = false
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmail.error = getString(R.string.invalid_email)
            valid = false
        } else {
            binding.tilEmail.error = null
        }

        // Validar teléfono
        val phone = binding.etPhone.text.toString().trim()
        if (phone.isEmpty()) {
            binding.tilPhone.error = getString(R.string.required_field)
            valid = false
        } else if (!isValidPhone(phone)) {
            binding.tilPhone.error = getString(R.string.invalid_phone)
            valid = false
        } else {
            binding.tilPhone.error = null
        }

        // Validar contraseña
        val password = binding.etPassword.text.toString().trim()
        if (password.isEmpty()) {
            binding.tilPassword.error = getString(R.string.required_field)
            valid = false
        } else if (password.length < 6) {
            binding.tilPassword.error = getString(R.string.password_too_short)
            valid = false
        } else {
            binding.tilPassword.error = null
        }

        // Validar confirmación de contraseña
        val confirmPassword = binding.etConfirmPassword.text.toString().trim()
        if (confirmPassword.isEmpty()) {
            binding.tilConfirmPassword.error = getString(R.string.required_field)
            valid = false
        } else if (confirmPassword != password) {
            binding.tilConfirmPassword.error = getString(R.string.passwords_not_match)
            valid = false
        } else {
            binding.tilConfirmPassword.error = null
        }

        // Validar aceptación de términos
        if (!binding.cbTerms.isChecked) {
            FlatterToast.showError(this, getString(R.string.terms_not_accepted))
            valid = false
        }

        return valid
    }

    private fun isValidPhone(phone: String): Boolean {
        // Patrón básico para números de teléfono españoles
        val phonePattern = "^(\\+[0-9]{1,3})?[0-9]{9}\$"
        return phone.matches(phonePattern.toRegex())
    }

    private fun registerUser() {
        showProgress(true)

        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        // Crear usuario en Firebase Auth
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // Registro exitoso, guardar datos adicionales en Firestore
                    val userId = auth.currentUser?.uid ?: ""
                    saveUserDataToFirestore(userId)
                } else {
                    // Error en el registro
                    showProgress(false)
                    val message = task.exception?.message ?: "Error desconocido"
                    FlatterToast.showError(
                        this,
                        "Error en el registro: $message"
                    )
                    Log.e(TAG, "Error al registrar: ${task.exception}")
                }
            }
    }

    // Updated saveUserDataToFirestore method in RegisterActivity.kt
    private fun saveUserDataToFirestore(userId: String) {
        // Obtener los datos del formulario
        val username = binding.etUsername.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val phone = binding.etPhone.text.toString().trim()

        // IMPORTANT: Make sure we're getting the correct user type
        val userType = if (binding.rbRenter.isChecked) "inquilino" else "propietario"

        // Debug log to verify user type selection
        Log.d(TAG, "Saving user with type: '$userType' for user: $username")
        Log.d(TAG, "Renter checked: ${binding.rbRenter.isChecked}, Owner checked: ${binding.rbOwner.isChecked}")

        // Crear objeto de usuario para Firestore
        val user = hashMapOf(
            "username" to username,
            "fullName" to username, // Asegurar que el username también se guarde como fullName
            "email" to email,
            "phone" to phone,
            "userType" to userType, // Make sure this is being saved correctly
            "createdAt" to com.google.firebase.Timestamp.now(),
            "profileImageUrl" to "",
            "bio" to "",
            "maxBudget" to 0.0 // Add default budget
        )

        // Debug log the complete user object
        Log.d(TAG, "Complete user object being saved: $user")

        // Guardar en Firestore
        db.collection("users").document(userId)
            .set(user)
            .addOnSuccessListener {
                showProgress(false)

                // Verify the user was saved correctly
                db.collection("users").document(userId).get()
                    .addOnSuccessListener { document ->
                        val savedUserType = document.getString("userType")
                        Log.d(TAG, "Verification - User saved with userType: '$savedUserType'")

                        FlatterToast.showSuccess(this, "¡Cuenta creada exitosamente!")

                        // Cerrar sesión del usuario para que tengan que iniciar sesión explícitamente
                        auth.signOut()

                        // Volver a LoginActivity
                        finish() // Esto volverá al LoginActivity si está en la pila de actividades
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Error verifying saved user: $e")
                        FlatterToast.showSuccess(this, "¡Cuenta creada exitosamente!")
                        auth.signOut()
                        finish()
                    }
            }
            .addOnFailureListener { e ->
                showProgress(false)
                FlatterToast.showError(
                    this,
                    "Error al guardar datos: ${e.message}"
                )
                Log.e(TAG, "Error al guardar datos: $e")

                // Eliminar la cuenta de Auth si falló el guardado en Firestore
                auth.currentUser?.delete()
            }
    }

    private fun showProgress(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.btnRegister.isEnabled = !show

        // Deshabilitar todos los campos durante la carga
        binding.etUsername.isEnabled = !show
        binding.etEmail.isEnabled = !show
        binding.etPhone.isEnabled = !show
        binding.etPassword.isEnabled = !show
        binding.etConfirmPassword.isEnabled = !show
        binding.rbRenter.isEnabled = !show
        binding.rbOwner.isEnabled = !show
        binding.cbTerms.isEnabled = !show
    }

    private fun setupPasswordStrengthMeter() {
        // Obtener referencias al campo de contraseña y medidor de fuerza
        val etPassword = binding.etPassword
        val passwordStrengthMeter = binding.passwordStrengthMeter
        val tvPasswordStrength = binding.tvPasswordStrength

        // Conectar el campo de contraseña al medidor de fuerza
        passwordStrengthMeter.setStrengthTextView(tvPasswordStrength)

        // Agregar un listener de cambio de texto al campo de contraseña
        etPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                // Calcular la fuerza de la contraseña y actualizar el medidor
                val password = s.toString()
                val strength = calculatePasswordStrength(password)
                passwordStrengthMeter.setStrength(strength)
            }
        })
    }

    // Función de cálculo de fuerza de contraseña
    private fun calculatePasswordStrength(password: String): Int {
        if (password.isEmpty()) return PasswordStrengthMeter.STRENGTH_WEAK

        var score = 0

        // Criterios de longitud
        if (password.length >= 8) score++
        if (password.length >= 12) score++

        // Criterios de complejidad
        if (password.any { it.isDigit() }) score++
        if (password.any { it.isLowerCase() }) score++
        if (password.any { it.isUpperCase() }) score++
        if (password.any { !it.isLetterOrDigit() }) score++

        return when {
            score <= 2 -> PasswordStrengthMeter.STRENGTH_WEAK
            score <= 4 -> PasswordStrengthMeter.STRENGTH_MEDIUM
            score <= 6 -> PasswordStrengthMeter.STRENGTH_STRONG
            else -> PasswordStrengthMeter.STRENGTH_VERY_STRONG
        }
    }
}