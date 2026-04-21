package com.example.civicconnect

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.civicconnect.adapters.IssueAdapter
import com.example.civicconnect.data.Issue
import com.example.civicconnect.databinding.FragmentMyIssuesBinding
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

class MyIssuesFragment : Fragment() {

    private var _binding: FragmentMyIssuesBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: IssueAdapter
    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private var allIssues: List<Issue> = emptyList()
    private var issuesListener: ListenerRegistration? = null

    private var highlightTracking: String? = null
    private var hasTriedHighlight = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMyIssuesBinding.inflate(inflater, container, false)
        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        highlightTracking = arguments?.getString("highlight_tracking")
        Log.d("HighlightFlow", "Received highlightTracking = $highlightTracking")

        setupRecyclerView()
        setupChips()
        setupSearch()
        loadIssues()

        return binding.root
    }

    private fun setupRecyclerView() {
        adapter = IssueAdapter(emptyList()) { selectedIssue ->
            val fragment = IssueDetailFragment.newInstance(
                selectedIssue.id,
                selectedIssue.ownerUid,
                selectedIssue.title,
                selectedIssue.description,
                selectedIssue.category,
                selectedIssue.location,
                selectedIssue.status,
                selectedIssue.trackingNumber,
                selectedIssue.imageUri
            )

            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .addToBackStack(null)
                .commit()
        }

        binding.recyclerMyIssues.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerMyIssues.adapter = adapter
    }

    private fun loadIssues() {
        val uid = auth.currentUser?.uid ?: return
        issuesListener?.remove()

        firestore.collection("users").document(uid)
            .get()
            .addOnSuccessListener { userDoc ->
                val isAdmin = userDoc.getString("userType") == "Administrator"

                val query = if (isAdmin) {
                    firestore.collection("all_issues")
                        .orderBy("timestamp", Query.Direction.DESCENDING)
                } else {
                    firestore.collection("all_issues")
                        .whereEqualTo("ownerUid", uid)
                        .orderBy("timestamp", Query.Direction.DESCENDING)
                }

                issuesListener = query.addSnapshotListener { snapshot, error ->
                    if (!isAdded || _binding == null) return@addSnapshotListener

                    if (error != null) {
                        Toast.makeText(requireContext(), "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                        return@addSnapshotListener
                    }

                    if (snapshot == null || snapshot.isEmpty) {
                        allIssues = emptyList()
                        adapter.updateData(emptyList())
                        return@addSnapshotListener
                    }

                    allIssues = snapshot.documents.map { doc ->
                        Issue(
                            id = doc.id,
                            ownerUid = doc.getString("ownerUid") ?: "",
                            title = doc.getString("title") ?: "",
                            category = doc.getString("category") ?: "",
                            description = doc.getString("description") ?: "",
                            location = doc.getString("location") ?: "",
                            imageUri = doc.getString("imageUrl"),
                            status = doc.getString("status") ?: "Pending",
                            trackingNumber = doc.getString("trackingNumber") ?: doc.id,
                            dateReported = doc.getLong("timestamp") ?: 0L,
                            userType = ""
                        )
                    }

                    filterIssues()
                }
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Failed to load issues.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterIssues()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setupChips() {
        binding.chipGroupFilter.setOnCheckedChangeListener { _, _ -> filterIssues() }
    }

    private fun filterIssues() {
        if (!isAdded || _binding == null) return

        val query = binding.etSearch.text.toString().trim().lowercase()
        val checkedChipId = binding.chipGroupFilter.checkedChipId
        val chipText = if (checkedChipId != View.NO_ID)
            binding.chipGroupFilter.findViewById<Chip>(checkedChipId).text.toString()
        else "All"

        var filtered = allIssues
        if (chipText != "All") filtered = filtered.filter { it.status.equals(chipText, true) }
        if (query.isNotEmpty()) {
            filtered = filtered.filter {
                it.title.lowercase().contains(query) ||
                        it.trackingNumber.lowercase().contains(query)
            }
        }

        adapter.updateData(filtered)

        // ✅ Try to scroll/highlight once AFTER data is visible
        if (!hasTriedHighlight && highlightTracking != null) {
            binding.recyclerMyIssues.postDelayed({
                highlightIfFound(filtered)
            }, 800)
        }
    }

    private fun highlightIfFound(filtered: List<Issue>) {
        val target = highlightTracking?.trim()?.uppercase() ?: return
        val pos = filtered.indexOfFirst { it.trackingNumber.trim().uppercase() == target }

        Log.d("HighlightFlow", "Trying to highlight $target at position $pos among ${filtered.size} items")

        if (pos != -1) {
            binding.recyclerMyIssues.smoothScrollToPosition(pos)
            binding.recyclerMyIssues.postDelayed({
                val holder = binding.recyclerMyIssues.findViewHolderForAdapterPosition(pos)
                if (holder != null) {
                    Log.d("HighlightFlow", "Highlighting card at $pos")
                    animateHighlight(holder.itemView)
                } else {
                    Log.w("HighlightFlow", "Holder still null, retrying in 500ms")
                    binding.recyclerMyIssues.postDelayed({
                        val retryHolder = binding.recyclerMyIssues.findViewHolderForAdapterPosition(pos)
                        if (retryHolder != null) {
                            animateHighlight(retryHolder.itemView)
                        } else {
                            Log.e("HighlightFlow", "Failed to find view even after retry.")
                        }
                    }, 500)
                }
            }, 600)
        } else {
            Log.w("HighlightFlow", "No issue found matching tracking number $target")
        }

        hasTriedHighlight = true
        arguments?.remove("highlight_tracking")
    }

    private fun animateHighlight(view: View) {
        val card = view.findViewById<MaterialCardView>(R.id.cardView) ?: return
        val startColor = ContextCompat.getColor(requireContext(), R.color.welcome_bg)
        val endColor = ContextCompat.getColor(requireContext(), android.R.color.white)

        val animator = ValueAnimator.ofObject(ArgbEvaluator(), startColor, endColor)
        animator.duration = 1200
        animator.addUpdateListener { va ->
            card.setCardBackgroundColor(va.animatedValue as Int)
        }
        card.setCardBackgroundColor(startColor)
        animator.start()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        issuesListener?.remove()
        _binding = null
    }

    override fun onResume() {
        super.onResume()
        filterIssues()
    }
}
