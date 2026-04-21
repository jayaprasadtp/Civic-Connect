package com.example.civicconnect

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.civicconnect.databinding.ActivityMainBinding
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var userType: String = "Citizen" // Default type

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // ✅ Fetch current user's type once
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .get()
                .addOnSuccessListener { doc ->
                    userType = doc.getString("userType") ?: "Citizen"
                    setupBottomNav() // ✅ initialize nav only after knowing user type
                }
                .addOnFailureListener {
                    setupBottomNav() // fallback if fails
                }
        } else {
            setupBottomNav()
        }
    }

    /** --- Setup bottom navigation dynamically based on user type --- */
    private fun setupBottomNav() {
        val bottomNav: BottomNavigationView = binding.bottomNavigation

        // Load Home by default
        loadFragment(HomeFragment())
        bottomNav.selectedItemId = R.id.nav_home

        // Handle clicks
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    loadFragment(HomeFragment())
                    true
                }
                R.id.nav_report -> {
                    loadFragment(ReportFragment())
                    true
                }
                R.id.nav_my_issues -> {
                    if (userType == "Administrator") {
                        loadFragment(AdminIssuesFragment())
                    } else {
                        loadFragment(MyIssuesFragment())
                    }
                    true
                }
                R.id.nav_profile -> {
                    loadFragment(ProfileFragment())
                    true
                }
                else -> false
            }
        }
    }

    /** --- Helper to load fragments cleanly --- */
    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
}
