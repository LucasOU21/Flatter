package com.example.flatter.loginVista

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.flatter.homeVista.HomeActivity
import com.example.flatter.R
import com.example.flatter.registerVista.RegisterActivity
import com.example.flatter.utils.FlatterToast
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import android.app.AlertDialog
import android.text.InputType
import android.widget.EditText

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Firebase Auth and Firestore
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Check if a user is already logged in
        val currentUser = auth.currentUser
        if (currentUser != null) {
            // Verify that the user exists in Firestore
            db.collection("users").document(currentUser.uid).get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        // User exists, redirect to HomeActivity
                        startActivity(Intent(this, HomeActivity::class.java))
                        finish()
                    } else {
                        // User doesn't exist in Firestore, sign them out
                        auth.signOut()
                        setContentView(R.layout.activity_login)
                        setupViews()
                    }
                }
                .addOnFailureListener {
                    // Error checking user, sign them out to be safe
                    auth.signOut()
                    setContentView(R.layout.activity_login)
                    setupViews()
                }
        } else {
            // No user is signed in, show login screen
            setContentView(R.layout.activity_login)
            setupViews()
        }
    }

    private fun setupViews() {
        val etEmail = findViewById<TextInputEditText>(R.id.etEmail)
        val etPassword = findViewById<TextInputEditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val tvRegister = findViewById<TextView>(R.id.tvRegister)

        // Login button
        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                FlatterToast.showError(this, "Rellena todos los campos")
            } else {
                loginUser(email, password)
            }
        }

        // Redirect to Registration
        tvRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        // Setup forgot password
        setupForgotPassword()
    }

    private fun setupForgotPassword() {
        val tvForgotPassword = findViewById<TextView>(R.id.tvForgotPassword)

        tvForgotPassword.setOnClickListener {
            showForgotPasswordDialog()
        }
    }

    private fun showForgotPasswordDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Restablecer contraseña")

        // Set up the input field
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        input.hint = "Correo electrónico"

        // Pre-fill with the email if already entered in the login form
        val loginEmail = findViewById<TextInputEditText>(R.id.etEmail).text.toString().trim()
        if (loginEmail.isNotEmpty()) {
            input.setText(loginEmail)
        }

        builder.setView(input)

        // Set up the buttons
        builder.setPositiveButton("Enviar") { dialog, _ ->
            val email = input.text.toString().trim()
            if (email.isNotEmpty()) {
                sendPasswordResetEmail(email)
            } else {
                FlatterToast.showError(this, "Por favor, introduce tu correo electrónico")
            }
            dialog.dismiss()
        }

        builder.setNegativeButton("Cancelar") { dialog, _ ->
            dialog.cancel()
        }

        builder.show()
    }

    private fun sendPasswordResetEmail(email: String) {
        findViewById<ProgressBar>(R.id.progressBar).visibility = ProgressBar.VISIBLE

        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                findViewById<ProgressBar>(R.id.progressBar).visibility = ProgressBar.GONE

                if (task.isSuccessful) {
                    FlatterToast.showSuccess(
                        this,
                        "Correo de restablecimiento enviado a $email"
                    )
                } else {
                    FlatterToast.showError(
                        this,
                        "Error: ${task.exception?.message}"
                    )
                }
            }
    }

    private fun loginUser(email: String, password: String) {
        findViewById<ProgressBar>(R.id.progressBar).visibility = ProgressBar.VISIBLE

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    // Verificar si el usuario existe en Firestore
                    val userId = auth.currentUser?.uid ?: ""
                    db.collection("users").document(userId).get()
                        .addOnSuccessListener { document ->
                            if (document.exists()) {
                                // Success message with custom toast
                                FlatterToast.showSuccess(this, "¡Bienvenido a Flatter!")

                                // Redirigir a la pantalla principal
                                startActivity(Intent(this, HomeActivity::class.java))
                                finish()
                            } else {
                                FlatterToast.showError(
                                    this,
                                    "Usuario no registrado en Firestore"
                                )
                                auth.signOut() // Cerrar sesión si no está en Firestore
                            }
                        }
                        .addOnFailureListener {
                            FlatterToast.showError(this, "Error al verificar usuario")
                        }
                } else {
                    FlatterToast.showError(this, "Error: ${task.exception?.message}")
                }
                findViewById<ProgressBar>(R.id.progressBar).visibility = ProgressBar.GONE
            }
    }
}