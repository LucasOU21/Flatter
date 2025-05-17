package com.example.flatter.registerVista

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.util.Patterns
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.flatter.PasswordStrengthMeter
import com.example.flatter.homeVista.HomeActivity
import com.example.flatter.R
import com.example.flatter.databinding.ActivityRegisterBinding
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

        //Volver a la pantalla de login
        binding.tvLogin.setOnClickListener {
            finish() // Regresa a la actividad anterior (Login)
        }

        //Aplicar animaciones a los campos al enfocarlos (opcional, para mejorar la interactividad)
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
            Toast.makeText(this, getString(R.string.terms_not_accepted), Toast.LENGTH_SHORT).show()
            valid = false
        }

        return valid
    }

    private fun isValidPhone(phone: String): Boolean {
        // Patrón básico para números de teléfono españoles
        // Acepta formatos: +34612345678, 612345678, etc.
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
                    Toast.makeText(
                        this,
                        getString(R.string.error_registration, message),
                        Toast.LENGTH_LONG
                    ).show()
                    Log.e(TAG, "Error al registrar: ${task.exception}")
                }
            }
    }

    private fun saveUserDataToFirestore(userId: String) {
        // Obtener los datos del formulario
        val username = binding.etUsername.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val phone = binding.etPhone.text.toString().trim()
        val userType = if (binding.rbRenter.isChecked) "inquilino" else "propietario"

        // Crear objeto de usuario para Firestore
        val user = hashMapOf(
            "username" to username,
            "fullName" to username, // Add this line to ensure the username is also saved as fullName
            "email" to email,
            "phone" to phone,
            "userType" to userType,
            "createdAt" to com.google.firebase.Timestamp.now(),
            "profileImageUrl" to "",
            "bio" to ""
        )

        // Guardar en Firestore
        db.collection("users").document(userId)
            .set(user)
            .addOnSuccessListener {
                showProgress(false)
                Toast.makeText(this, getString(R.string.registration_success), Toast.LENGTH_SHORT).show()

                // Redirigir a HomeActivity
                val intent = Intent(this, HomeActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            .addOnFailureListener { e ->
                showProgress(false)
                Toast.makeText(
                    this,
                    getString(R.string.error_registration, e.message),
                    Toast.LENGTH_LONG
                ).show()
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
        // Get references to the password field and strength meter
        val etPassword = binding.etPassword
        val passwordStrengthMeter = binding.passwordStrengthMeter
        val tvPasswordStrength = binding.tvPasswordStrength

        // Connect the password field to the strength meter
        passwordStrengthMeter.setStrengthTextView(tvPasswordStrength)

        // Add a text change listener to the password field
        etPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                // Calculate the password strength and update the meter
                val password = s.toString()
                val strength = calculatePasswordStrength(password)
                passwordStrengthMeter.setStrength(strength)
            }
        })
    }

    // Password strength calculation function
    private fun calculatePasswordStrength(password: String): Int {
        if (password.isEmpty()) return PasswordStrengthMeter.STRENGTH_WEAK

        var score = 0

        // Length criteria
        if (password.length >= 8) score++
        if (password.length >= 12) score++

        // Complexity criteria
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