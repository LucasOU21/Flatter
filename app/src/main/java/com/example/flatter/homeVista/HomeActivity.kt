package com.example.flatter.homeVista
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.flatter.chatVista.ChatsFragment
import com.example.flatter.ProfileFragment
import com.example.flatter.R
import com.example.flatter.databinding.ActivityHomeBinding
import com.example.flatter.listingVista.ListingManagerFragment
import com.example.flatter.utils.FlatterToast
import com.google.firebase.auth.FirebaseAuth

class HomeActivity : AppCompatActivity() {
    private val TAG = "FlattrDebug"
    private lateinit var binding: ActivityHomeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            // Initialize ViewBinding
            binding = ActivityHomeBinding.inflate(layoutInflater)
            setContentView(binding.root)

            // Set up bottom navigation
            binding.bottomNavigation.setOnItemSelectedListener { item ->
                when (item.itemId) {
                    R.id.navigation_chats -> loadFragment(ChatsFragment())
                    R.id.navigation_home -> loadFragment(HomeFragment())
                    R.id.navigation_profile -> loadFragment(ProfileFragment())
                    R.id.navigation_create -> {
                        // Check if user is logged in
                        if (FirebaseAuth.getInstance().currentUser != null) {
                            // Load the ListingManagerFragment instead of starting CreateListingActivity
                            loadFragment(ListingManagerFragment())
                            true
                        } else {
                            FlatterToast.showError(this, "Debes iniciar sesión para gestionar anuncios")
                            false
                        }
                    }
                    else -> false
                }
            }

            // Load default fragment
            if (savedInstanceState == null) {
                binding.bottomNavigation.selectedItemId = R.id.navigation_home
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate: ${e.message}", e)
        }
    }

    private fun loadFragment(fragment: Fragment): Boolean {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
        return true
    }
}