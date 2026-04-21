package com.example.civicconnect

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
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

    private var similarityScore: Double? = null
    private var duplicateTrackingNumber: String? = null

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
            imageUrl: String? = null,
            similarityScore: Double? = null,
            duplicateTrackingNumber: String? = null
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
                if (similarityScore != null) putDouble("similarityScore", similarityScore)
                putString("duplicateTrackingNumber", duplicateTrackingNumber)
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

            if (it.containsKey("similarityScore")) {
                similarityScore = it.getDouble("similarityScore")
            }
            duplicateTrackingNumber = it.getString("duplicateTrackingNumber")
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

        if (similarityScore != null) {
            binding.tvSimilarityScore.visibility = View.VISIBLE
            binding.tvSimilarityScore.text = "Similarity Score: ${"%.3f".format(similarityScore)}"
        } else {
            binding.tvSimilarityScore.visibility = View.GONE
        }

        if (!duplicateTrackingNumber.isNullOrBlank()) {
            binding.tvSimilarToTracking.visibility = View.VISIBLE
            binding.tvSimilarToTracking.text = "Similar to: $duplicateTrackingNumber"
        } else {
            binding.tvSimilarToTracking.visibility = View.GONE
        }

        if (!imageUrl.isNullOrEmpty()) {
            binding.cardIssueImage.visibility = View.VISIBLE
            binding.ivIssueImage.visibility = View.VISIBLE
            Glide.with(requireContext())
                .load(imageUrl)
                .centerCrop()
                .placeholder(R.drawable.ic_placeholder)
                .error(R.drawable.ic_placeholder)
                .into(binding.ivIssueImage)

            binding.ivIssueImage.setOnClickListener {
                val previewUrl = imageUrl ?: return@setOnClickListener
                ImagePreviewDialogFragment.show(this, previewUrl)
            }
        } else {
            binding.cardIssueImage.visibility = View.GONE
            binding.ivIssueImage.visibility = View.GONE
            binding.ivIssueImage.setOnClickListener(null)
        }

        val adapter = ArrayAdapter.createFromResource(
            requireContext(),
            R.array.issue_status_list,
            android.R.layout.simple_list_item_1
        )
        binding.spStatus.setAdapter(adapter)

        binding.layoutAdminControls.visibility = if (isAdmin) View.VISIBLE else View.GONE

        binding.btnAddRemark.setOnClickListener { addRemark() }
        binding.btnUpdateStatus.setOnClickListener { updateStatus() }
    }

    /** ✅ Read issue details from /all_issues */
    private fun loadIssueDetails() {
        if (issueId.isNullOrBlank()) {
            showAppToast("Missing issue ID.")
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

                val similarityScoreValue = doc.getDouble("similarityScore")
                val duplicateTrackingValue = doc.getString("duplicateTrackingNumber")

                val formattedDate = timestamp?.let {
                    SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(it))
                } ?: "-"

                binding.tvDate.text = formattedDate
                binding.tvRemarks.text = if (remarks.isEmpty()) {
                    "No remarks yet."
                } else {
                    remarks.joinToString("\n• ", prefix = "• ") { it["text"].toString() }
                }

                val priorityBand = PriorityBands.fromScore(priorityScore)

                binding.tvPriority.text = "Priority: ${priorityBand.label}"
                val bg = binding.tvPriority.background?.mutate() as? GradientDrawable
                bg?.setColor(requireContext().getColor(priorityBand.colorRes))

                if (similarityScoreValue != null) {
                    binding.tvSimilarityScore.visibility = View.VISIBLE
                    binding.tvSimilarityScore.text =
                        "Similarity Score: ${"%.3f".format(similarityScoreValue)}"
                } else {
                    binding.tvSimilarityScore.visibility = View.GONE
                }

                if (!duplicateTrackingValue.isNullOrBlank()) {
                    binding.tvSimilarToTracking.visibility = View.VISIBLE
                    binding.tvSimilarToTracking.text = "Similar to: $duplicateTrackingValue"
                } else {
                    binding.tvSimilarToTracking.visibility = View.GONE
                }
            }
            .addOnFailureListener {
                showAppToast("Failed to load issue details.")
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
            showAppToast("Please enter a remark.")
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
                showAppToast("Remark added.")
                loadIssueDetails()
            }
            .addOnFailureListener {
                showAppToast("Failed to add remark.")
            }
    }

    /** ✅ Update status in /all_issues/{issueId} */
    private fun updateStatus() {
        val newStatus = binding.spStatus.text?.toString()?.trim()
        if (newStatus.isNullOrEmpty()) {
            showAppToast("Select a valid status.")
            return
        }

        firestore.collection("all_issues")
            .document(issueId!!)
            .update("status", newStatus)
            .addOnSuccessListener {
                showAppToast("Status updated to $newStatus")
                binding.tvStatus.text = newStatus
                loadIssueDetails()
            }
            .addOnFailureListener {
                showAppToast("Failed to update status.")
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
