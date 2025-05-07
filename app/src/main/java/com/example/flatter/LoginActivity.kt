package com.example.flatter

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicializar Firebase Auth y Firestore
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Comprobar si ya hay un usuario conectado
        val currentUser = auth.currentUser
        if (currentUser != null) {
            // Verificar si el usuario existe en Firestore
            db.collection("users").document(currentUser.uid).get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        // El usuario existe, redirigir a HomeActivity
                        startActivity(Intent(this, HomeActivity::class.java))
                        finish()
                    }
                }
        }

        setContentView(R.layout.activity_login)

        val etEmail = findViewById<TextInputEditText>(R.id.etEmail)
        val etPassword = findViewById<TextInputEditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val tvRegister = findViewById<TextView>(R.id.tvRegister)

        // Botón de Login
        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Rellena todos los campos", Toast.LENGTH_SHORT).show()
            } else {
                loginUser(email, password)
            }
        }

        // Redirigir a Registro
        //tvRegister.setOnClickListener {
        //    startActivity(Intent(this, RegisterActivity::class.java))
        //}
    }

    private fun loginUser(email: String, password: String) {
        findViewById<ProgressBar>(R.id.progressBar).visibility = ProgressBar.VISIBLE

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    //verificar si el usuario existe en Firestore
                    val userId = auth.currentUser?.uid ?: ""
                    db.collection("users").document(userId).get()
                        .addOnSuccessListener { document ->
                            if (document.exists()) {
                                //redirigir a la pantalla principal
                                startActivity(Intent(this, HomeActivity::class.java))
                                finish()
                            } else {
                                Toast.makeText(
                                    this,
                                    "Usuario no registrado en Firestore",
                                    Toast.LENGTH_SHORT
                                ).show()
                                auth.signOut()  //cerrar sesión si no está en Firestore
                            }
                        }
                        .addOnFailureListener {
                            Toast.makeText(this, "Error al verificar usuario", Toast.LENGTH_SHORT)
                                .show()
                        }
                } else {
                    Toast.makeText(this, "Error: ${task.exception?.message}", Toast.LENGTH_SHORT)
                        .show()
                }
                findViewById<ProgressBar>(R.id.progressBar).visibility = ProgressBar.GONE
            }
    }
}