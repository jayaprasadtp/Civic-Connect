package com.example.civicconnect

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.civicconnect.databinding.FragmentProfileBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        loadUserData()
        loadIssueStats()
        setupSettings()

        return binding.root
    }

    /** --- 1️⃣ Load user info --- */
    private fun loadUserData() {
        val uid = auth.currentUser?.uid ?: return

        firestore.collection("users").document(uid)
            .get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    binding.tvUserName.text = doc.getString("fullName") ?: "User"
                    binding.tvFullName.text = doc.getString("fullName") ?: "-"
                    binding.tvEmail.text = doc.getString("email") ?: "-"
                    binding.tvContact.text = doc.getString("phone") ?: "-"
                    binding.tvAddress.text = doc.getString("address") ?: "-"

                    // Load stats after knowing user type
                    val userType = doc.getString("userType") ?: "Citizen"
                    loadIssueStats(userType)
                }
            }
            .addOnFailureListener {
                showAppToast("Failed to load profile.")
            }
    }

    /** --- 2️⃣ Load issue statistics --- */
    private fun loadIssueStats(userType: String = "Citizen") {
        val uid = auth.currentUser?.uid ?: return

        val query = if (userType == "Administrator") {
            firestore.collection("all_issues")
        } else {
            firestore.collection("all_issues").whereEqualTo("ownerUid", uid)
        }

        query.get()
            .addOnSuccessListener { snapshot ->
                val nonRejectedReports = snapshot.documents.filter {
                    it.getString("status") != "Rejected"
                }

                val totalNonRejected = nonRejectedReports.size

                val inProgress = nonRejectedReports.count {
                    val status = it.getString("status")
                    status == "In Progress" || status == "Under Review"
                }

                val resolved = nonRejectedReports.count {
                    it.getString("status") == "Resolved"
                }

                val rate = if (totalNonRejected > 0) {
                    resolved * 100.0 / totalNonRejected
                } else {
                    0.0
                }

                binding.tvReported.text = totalNonRejected.toString()
                binding.tvResolved.text = resolved.toString()
                binding.tvResolutionRate.text = String.format("%.1f%%", rate)
            }
            .addOnFailureListener {
                showAppToast("Failed to load issue stats.")
            }
    }

    /** --- 3️⃣ Settings Rows --- */
    private fun setupSettings() {
        val rowChangePassword = binding.rowChangePassword.root
        val rowLogout = binding.rowNotifications.root

        rowChangePassword.findViewById<TextView>(R.id.tvSettingTitle).text = "Change Password"
        rowChangePassword.findViewById<TextView>(R.id.tvSettingHint).text =
            "Send a reset link to your email"
        rowLogout.findViewById<TextView>(R.id.tvSettingTitle).text = "Logout"
        rowLogout.findViewById<TextView>(R.id.tvSettingHint).text =
            "Sign out from this device safely"

        rowChangePassword.findViewById<ImageView>(R.id.imgSettingIcon)
            .setImageResource(R.drawable.ic_lock)
        rowLogout.findViewById<ImageView>(R.id.imgSettingIcon)
            .setImageResource(R.drawable.ic_logout)

        // Reset password
        rowChangePassword.setOnClickListener {
            val email = auth.currentUser?.email
            if (email.isNullOrEmpty()) {
                showAppToast("No email found for user.")
            } else {
                auth.sendPasswordResetEmail(email)
                    .addOnSuccessListener {
                        showAppToast("Password reset email sent.")
                    }
                    .addOnFailureListener { e ->
                        showAppToast("Error: ${e.message}")
                    }
            }
        }

        // Logout
        rowLogout.setOnClickListener {
            auth.signOut()
            showAppToast("Logged out.")
            startActivity(Intent(requireContext(), LoginActivity::class.java))
            requireActivity().finish()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
