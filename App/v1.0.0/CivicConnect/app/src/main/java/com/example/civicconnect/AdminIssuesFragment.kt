package com.example.civicconnect

import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.civicconnect.data.Issue
import com.example.civicconnect.databinding.FragmentMyIssuesBinding
import com.google.android.material.chip.Chip
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

class AdminIssuesFragment : Fragment() {

    private var _binding: FragmentMyIssuesBinding? = null
    private val binding get() = _binding!!

    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private var allIssues: List<Issue> = emptyList()
    private var issuesListener: ListenerRegistration? = null
    private lateinit var adapter: AdminIssueAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMyIssuesBinding.inflate(inflater, container, false)
        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        setupRecycler()
        setupChips()
        setupSearch()
        listenAllIssues()

        return binding.root
    }

    /** ✅ RecyclerView Setup */
    private fun setupRecycler() {
        adapter = AdminIssueAdapter { issue ->
            parentFragmentManager.beginTransaction()
                .replace(
                    R.id.fragment_container,
                    IssueDetailFragment.newInstance(
                        issue.id,
                        issue.ownerUid ?: "",
                        issue.title,
                        issue.description,
                        issue.category,
                        issue.location,
                        issue.status,
                        issue.trackingNumber,
                        issue.imageUri
                    )
                )
                .addToBackStack(null)
                .commit()
        }

        binding.recyclerMyIssues.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerMyIssues.adapter = adapter
    }

    /** ✅ Listen for all issues (admin view) */
    private fun listenAllIssues() {
        issuesListener?.remove()
        issuesListener = firestore.collection("all_issues")
            .orderBy("timestamp", Query.Direction.DESCENDING) // base order for recency
            .addSnapshotListener { snap, err ->
                if (!isAdded || _binding == null) return@addSnapshotListener

                if (err != null) {
                    Toast.makeText(requireContext(), err.message, Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                if (snap == null || snap.isEmpty) {
                    adapter.submit(emptyList())
                    return@addSnapshotListener
                }

                allIssues = snap.documents.map { d ->
                    Issue(
                        id = d.id,
                        ownerUid = d.getString("ownerUid") ?: "",
                        title = d.getString("title") ?: "",
                        category = d.getString("category") ?: "",
                        description = d.getString("description") ?: "",
                        location = d.getString("location") ?: "",
                        imageUri = d.getString("imageUrl"),
                        status = d.getString("status") ?: "Pending",
                        trackingNumber = d.getString("trackingNumber") ?: d.id,
                        dateReported = d.getLong("timestamp") ?: 0L,
                        priorityScore = d.getDouble("priorityScore") ?: 0.0,
                        duplicateOf = d.getString("duplicateOf"),
                        remarksCount = (d.getLong("remarksCount") ?: 0L).toInt(),
                        docPath = d.reference.path
                    )
                }

                // 🧠 Custom intelligent ordering:
                // 1️⃣ Pending → Under Review → In Progress → Resolved → Rejected
                // 2️⃣ Within each group, higher priority first
                // 3️⃣ Then newest first
                val statusRank = mapOf(
                    "pending" to 1,
                    "under review" to 2,
                    "in progress" to 3,
                    "resolved" to 4,
                    "rejected" to 5
                )

                val sorted = allIssues.sortedWith(
                    compareBy<Issue> { statusRank[it.status.lowercase()] ?: 99 }
                        .thenByDescending { it.priorityScore }
                        .thenByDescending { it.dateReported }
                )

                adapter.submit(sorted)
            }
    }

    /** ✅ Chip Filter */
    private fun setupChips() {
        binding.chipGroupFilter.setOnCheckedChangeListener { _, _ -> filterAndShow() }
    }

    /** ✅ Search Field */
    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterAndShow()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    /** ✅ Apply Filters + Search */
    private fun filterAndShow() {
        val q = binding.etSearch.text.toString().trim().lowercase()
        val checkedId = binding.chipGroupFilter.checkedChipId
        val chipText = if (checkedId != View.NO_ID)
            binding.chipGroupFilter.findViewById<Chip>(checkedId).text.toString()
        else "All"

        var list = allIssues
        if (chipText != "All") list = list.filter { it.status.equals(chipText, true) }
        if (q.isNotEmpty()) {
            list = list.filter {
                it.title.lowercase().contains(q) || it.trackingNumber.lowercase().contains(q)
            }
        }

        adapter.submit(list)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        issuesListener?.remove()
        _binding = null
    }
}

/** ✅ Admin Issue Adapter — Now Colorful Like User Side */
private class AdminIssueAdapter(
    private val onClick: (Issue) -> Unit
) : RecyclerView.Adapter<AdminIssueAdapter.VH>() {

    private val data = mutableListOf<Issue>()

    fun submit(list: List<Issue>) {
        data.clear()
        data.addAll(list)
        notifyDataSetChanged()
    }

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(R.id.tvIssueTitle)
        val status: TextView = v.findViewById(R.id.tvStatusBadge)
        val tvTracking: TextView = v.findViewById(R.id.tvTrackingNumber)
        val tvCategory: TextView = v.findViewById(R.id.tvCategory)
        val tvDate: TextView = v.findViewById(R.id.tvDate)
        val tvPriority: TextView = v.findViewById(R.id.tvPriority)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_issue_card, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val context = holder.itemView.context
        val issue = data[position]

        holder.title.text = issue.title
        holder.tvTracking.text = "#${issue.trackingNumber}"
        holder.tvCategory.text = issue.category
        holder.tvDate.text =
            android.text.format.DateFormat.format("MMM d, yyyy", issue.dateReported)

        // 🎨 Status badge color
        val statusColor = when (issue.status.lowercase()) {
            "pending" -> ContextCompat.getColor(context, R.color.colorDefault)
            "under review" -> ContextCompat.getColor(context, R.color.colorInProgress)
            "in progress" -> ContextCompat.getColor(context, R.color.colorReported)
            "resolved" -> ContextCompat.getColor(context, R.color.colorResolved)
            "rejected" -> ContextCompat.getColor(context, R.color.priority_high)
            else -> ContextCompat.getColor(context, R.color.colorDefault)
        }
        holder.status.text = issue.status
        holder.status.setBackgroundColor(statusColor)
        holder.status.setTextColor(Color.WHITE)

        // 💡 Always show priority label
        val score = issue.priorityScore.coerceIn(0.0, 1.0)
        val (label, colorRes) = when {
            score >= 0.75 -> "High" to R.color.priority_high
            score >= 0.4 -> "Medium" to R.color.priority_medium
            else -> "Low" to R.color.priority_low
        }

        holder.tvPriority.visibility = View.VISIBLE
        holder.tvPriority.text = "Priority: $label"
        holder.tvPriority.setTextColor(ContextCompat.getColor(context, colorRes))

        // 🚫 Mark duplicates clearly
        if (issue.duplicateOf != null) {
            holder.status.text = "Rejected"
            holder.status.setBackgroundColor(
                ContextCompat.getColor(context, R.color.priority_high)
            )
        }

        // 👆 Click listener
        holder.itemView.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) onClick(data[pos])
        }
    }

    override fun getItemCount() = data.size
}

