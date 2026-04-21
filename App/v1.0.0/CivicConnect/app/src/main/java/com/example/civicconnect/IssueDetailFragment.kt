package com.example.civicconnect

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.example.civicconnect.databinding.FragmentIssueDetailBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class IssueDetailFragment : Fragment() {

    private var _binding: FragmentIssueDetailBinding? = null
    private val binding get() = _binding!!

    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    private var issueId: String? = null
    private var ownerUid: String? = null
    private var title: String? = null
    private var desc: String? = null
    private var category: String? = null
    private var location: String? = null
    private var status: String? = null
    private var trackingNumber: String? = null
    private var imageUrl: String? = null

    private var isAdmin: Boolean = false

    companion object {
        fun newInstance(
            id: String,
            ownerUid: String,
            title: String,
            desc: String,
            category: String,
            location: String,
            status: String,
            trackingNumber: String,
            imageUrl: String? = null
        ): IssueDetailFragment {
            val fragment = IssueDetailFragment()
            val args = Bundle().apply {
                putString("id", id)
                putString("ownerUid", ownerUid)
                putString("title", title)
                putString("desc", desc)
                putString("category", category)
                putString("location", location)
                putString("status", status)
                putString("trackingNumber", trackingNumber)
                putString("imageUrl", imageUrl)
            }
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentIssueDetailBinding.inflate(inflater, container, false)
        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        arguments?.let {
            issueId = it.getString("id")
            ownerUid = it.getString("ownerUid") ?: auth.currentUser?.uid
            title = it.getString("title")
            desc = it.getString("desc")
            category = it.getString("category")
            location = it.getString("location")
            status = it.getString("status")
            trackingNumber = it.getString("trackingNumber")
            imageUrl = it.getString("imageUrl")
        }

        checkUserTypeAndSetup()
        return binding.root
    }

    private fun checkUserTypeAndSetup() {
        val uid = auth.currentUser?.uid ?: return
        firestore.collection("users").document(uid)
            .get()
            .addOnSuccessListener { doc ->
                isAdmin = doc.getString("userType") == "Administrator"
                setupUI()
                loadIssueDetails()
            }
            .addOnFailureListener {
                setupUI()
                loadIssueDetails()
            }
    }

    private fun setupUI() {
        binding.tvTitle.text = title
        binding.tvCategory.text = category
        binding.tvLocation.text = location
        binding.tvStatus.text = status
        binding.tvTracking.text = "#$trackingNumber"
        binding.tvDescription.text = desc

        // Load image
        if (!imageUrl.isNullOrEmpty()) {
            binding.ivIssueImage.visibility = View.VISIBLE
            Glide.with(requireContext()).load(imageUrl).into(binding.ivIssueImage)
        } else {
            binding.ivIssueImage.visibility = View.GONE
        }

        // Initialize dropdown properly
        val adapter = ArrayAdapter.createFromResource(
            requireContext(),
            R.array.issue_status_list,
            android.R.layout.simple_list_item_1
        )
        binding.spStatus.setAdapter(adapter)

        // Show admin controls if user is admin
        binding.layoutAdminControls.visibility = if (isAdmin) View.VISIBLE else View.GONE

        // Click listeners
        binding.btnAddRemark.setOnClickListener { addRemark() }
        binding.btnUpdateStatus.setOnClickListener { updateStatus() }
    }

    /** ✅ Read issue details from /all_issues */
    private fun loadIssueDetails() {
        if (issueId.isNullOrBlank()) {
            Toast.makeText(requireContext(), "Missing issue ID.", Toast.LENGTH_SHORT).show()
            return
        }

        firestore.collection("all_issues")
            .document(issueId!!)
            .get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) return@addOnSuccessListener

                val timestamp = doc.getLong("timestamp")
                val remarks = (doc.get("remarks") as? List<Map<String, Any>>) ?: emptyList()
                val priorityScore = (doc.getDouble("priorityScore") ?: 0.0).coerceIn(0.0, 1.0)

                // 🕒 Format date
                val formattedDate = timestamp?.let {
                    SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(it))
                } ?: "-"

                binding.tvDate.text = formattedDate
                binding.tvRemarks.text = if (remarks.isEmpty()) {
                    "No remarks yet."
                } else {
                    remarks.joinToString("\n• ", prefix = "• ") { it["text"].toString() }
                }

                // 🎨 Determine priority label and color
                val (label, colorRes) = when {
                    priorityScore >= 0.75 -> "High" to R.color.priority_high
                    priorityScore >= 0.4 -> "Medium" to R.color.priority_medium
                    else -> "Low" to R.color.priority_low
                }

                // 🟩 Always display priority label
                binding.tvPriority.text = "Priority: $label"
                val bg = binding.tvPriority.background?.mutate() as? GradientDrawable
                bg?.setColor(requireContext().getColor(colorRes))
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Failed to load issue details.", Toast.LENGTH_SHORT).show()
            }
    }


    private fun colorPriorityPill(priority: Int) {
        val bg = binding.tvPriority.background?.mutate() as? GradientDrawable ?: return
        val color = when (priority) {
            1 -> requireContext().getColor(R.color.priority_high)
            2 -> requireContext().getColor(R.color.priority_medium)
            3 -> requireContext().getColor(R.color.priority_low)
            else -> requireContext().getColor(android.R.color.darker_gray)
        }
        bg.setColor(color)
    }

    /** ✅ Add remark to /all_issues/{issueId} */
    private fun addRemark() {
        val remarkText = binding.etRemark.text.toString().trim()
        if (remarkText.isEmpty()) {
            Toast.makeText(requireContext(), "Please enter a remark.", Toast.LENGTH_SHORT).show()
            return
        }

        val remark = mapOf(
            "text" to remarkText,
            "by" to if (isAdmin) "Admin" else "Citizen",
            "at" to System.currentTimeMillis()
        )

        firestore.collection("all_issues")
            .document(issueId!!)
            .update(
                mapOf(
                    "remarks" to FieldValue.arrayUnion(remark),
                    "remarksCount" to FieldValue.increment(1)
                )
            )
            .addOnSuccessListener {
                binding.etRemark.text?.clear()
                Toast.makeText(requireContext(), "Remark added.", Toast.LENGTH_SHORT).show()
                loadIssueDetails()
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Failed to add remark.", Toast.LENGTH_SHORT).show()
            }
    }

    /** ✅ Update status in /all_issues/{issueId} */
    private fun updateStatus() {
        val newStatus = binding.spStatus.text?.toString()?.trim()
        if (newStatus.isNullOrEmpty()) {
            Toast.makeText(requireContext(), "Select a valid status.", Toast.LENGTH_SHORT).show()
            return
        }

        firestore.collection("all_issues")
            .document(issueId!!)
            .update("status", newStatus)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Status updated to $newStatus", Toast.LENGTH_SHORT).show()
                binding.tvStatus.text = newStatus
                loadIssueDetails()
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Failed to update status.", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
