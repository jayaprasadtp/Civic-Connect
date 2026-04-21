package com.example.civicconnect

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import com.example.civicconnect.databinding.ActivityLoginBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var isRegisterMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val userTypes = listOf("Citizen", "Administrator")
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, userTypes)
        binding.spinnerUserType.setAdapter(adapter)
        binding.spinnerUserType.setOnClickListener { binding.spinnerUserType.showDropDown() }

        binding.tvSwitch.setOnClickListener { toggleMode() }
        binding.tvForgotPassword.setOnClickListener { sendResetLink() }
        binding.btnAction.setOnClickListener {
            if (isRegisterMode) registerUser() else loginUser()
        }
    }

    private fun toggleMode() {
        isRegisterMode = !isRegisterMode
        if (isRegisterMode) {
            binding.tvTitle.text = "Register for Civic Connect"
            binding.btnAction.text = "Register"
            binding.tvSwitch.text = "Already have an account? Login"
            binding.registerFields.visibility = View.VISIBLE
            binding.tvForgotPassword.visibility = View.GONE
        } else {
            binding.tvTitle.text = "Login to Civic Connect"
            binding.btnAction.text = "Login"
            binding.tvSwitch.text = "Don’t have an account? Register"
            binding.registerFields.visibility = View.GONE
            binding.tvForgotPassword.visibility = View.VISIBLE
        }
    }

    private fun sendResetLink() {
        val email = binding.etEmail.text.toString().trim()

        if (email.isEmpty()) {
            showAppToast("Enter your email first.")
            return
        }

        auth.sendPasswordResetEmail(email)
            .addOnSuccessListener {
                showAppToast("Reset link has been sent to your mail.")
            }
            .addOnFailureListener { e ->
                showAppToast("Failed to send reset link: ${e.message}", true)
            }
    }

    private fun registerUser() {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()
        val fullName = binding.etFullName.text.toString().trim()
        val phone = binding.etPhone.text.toString().trim()
        val address = binding.etAddress.text.toString().trim()
        val userType = binding.spinnerUserType.text.toString().trim()

        if (email.isEmpty() || password.isEmpty() || fullName.isEmpty() ||
            phone.isEmpty() || address.isEmpty() || userType.isEmpty()
        ) {
            showAppToast("Please fill all fields")
            return
        }

        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val uid = result.user?.uid ?: return@addOnSuccessListener
                val user = hashMapOf(
                    "uid" to uid,
                    "email" to email,
                    "fullName" to fullName,
                    "phone" to phone,
                    "address" to address,
                    "userType" to userType,
                    "timestamp" to System.currentTimeMillis()
                )

                db.collection("users").document(uid)
                    .set(user)
                    .addOnSuccessListener {
                        auth.signOut()
                        if (isRegisterMode) toggleMode()
                        showAppToast("Account created. Please log in.")
                        binding.etPassword.text?.clear()
                    }
                    .addOnFailureListener { e ->
                        showAppToast("Firestore error: ${e.message}", true)
                    }
            }
            .addOnFailureListener { e ->
                showAppToast("Auth error: ${e.message}", true)
            }
    }

    private fun loginUser() {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        if (email.isEmpty() || password.isEmpty()) {
            showAppToast("Please enter email and password")
            return
        }

        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val uid = result.user?.uid ?: return@addOnSuccessListener
                fetchUserData(uid)
            }
            .addOnFailureListener { e ->
                showAppToast("Login failed: ${e.message}", true)
            }
    }

    private fun fetchUserData(uid: String) {
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val prefs = getSharedPreferences("CivicPrefs", MODE_PRIVATE)
                    prefs.edit().apply {
                        putString("uid", uid)
                        putString("fullName", doc.getString("fullName"))
                        putString("email", doc.getString("email"))
                        putString("phone", doc.getString("phone"))
                        putString("address", doc.getString("address"))
                        putString("userType", doc.getString("userType"))
                        apply()
                    }

                    showAppToast("Welcome, ${doc.getString("fullName")}!")

                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                } else {
                    showAppToast("User data not found", true)
                }
            }
            .addOnFailureListener { e ->
                showAppToast("Failed to load profile: ${e.message}", true)
            }
    }
}
