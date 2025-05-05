// app/src/main/java/com/example/flatter/HomeActivity.kt
package com.example.flatter

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.flatter.databinding.ActivityHomeBinding

class HomeActivity : AppCompatActivity() {
    private val TAG = "FlattrDebug"
    private lateinit var binding: ActivityHomeBinding
    private lateinit var homeViewModel: HomeViewModel
    private lateinit var userViewModel: UserViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "Starting onCreate")
        try {
            super.onCreate(savedInstanceState)

            binding = ActivityHomeBinding.inflate(layoutInflater)
            setContentView(binding.root)

            // Inicializar ViewModels
            homeViewModel = ViewModelProvider(this)[HomeViewModel::class.java]
            userViewModel = ViewModelProvider(this)[UserViewModel::class.java]

            // Configurar navegación
            setupBottomNavigation()

            // Cargar fragment inicial
            if (savedInstanceState == null) {
                loadFragment(HomeFragment())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate", e)
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_chats -> loadFragment(ChatsFragment())
                R.id.navigation_home -> loadFragment(HomeFragment())
                R.id.navigation_profile -> loadFragment(ProfileFragment())
                else -> false
            }
        }
    }

    private fun loadFragment(fragment: Fragment): Boolean {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
        return true
    }
}