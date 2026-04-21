package com.example.civicconnect

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.widget.ImageViewCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.civicconnect.data.Issue
import com.example.civicconnect.databinding.FragmentHomeBinding
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: IssuesAdapter
    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private var issuesListener: ListenerRegistration? = null
    private var userListener: ListenerRegistration? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)

        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        setupRecyclerView()
        setupButtons()
        loadUserData()
        listenToUserIssues()

        return binding.root
    }

    /** --- 1️⃣ RecyclerView Setup --- */
    private fun setupRecyclerView() {
        adapter = IssuesAdapter(emptyList()) { issue ->
            if (!isAdded || _binding == null) return@IssuesAdapter

            // navigate to MyIssuesFragment and highlight this issue
            val myIssuesFragment = MyIssuesFragment().apply {
                arguments = Bundle().apply {
                    putString("highlight_tracking", issue.trackingNumber)
                }
            }

            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, myIssuesFragment)
                .addToBackStack(null)
                .commitAllowingStateLoss() // ✅ safer transaction

            requireActivity()
                .findViewById<BottomNavigationView>(R.id.bottom_navigation)
                .selectedItemId = R.id.nav_my_issues
        }

        binding.recyclerRecentIssues.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerRecentIssues.adapter = adapter
    }

    /** --- 2️⃣ Report Buttons --- */
    private fun setupButtons() {
        val goToReport = View.OnClickListener {
            if (!isAdded || _binding == null) return@OnClickListener
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, ReportFragment())
                .addToBackStack(null)
                .commitAllowingStateLoss()

            requireActivity()
                .findViewById<BottomNavigationView>(R.id.bottom_navigation)
                .selectedItemId = R.id.nav_report
        }

        binding.btnReportNewIssue.setOnClickListener(goToReport)
        binding.btnReportFirstIssue.setOnClickListener(goToReport)
    }

    /** --- 3️⃣ Load Current User Info --- */
    private fun loadUserData() {
        val uid = auth.currentUser?.uid ?: return
        val userRef = firestore.collection("users").document(uid)

        userListener?.remove()
        userListener = userRef.addSnapshotListener { doc, error ->
            if (!isAdded || _binding == null) return@addSnapshotListener

            if (error != null) {
                Toast.makeText(requireContext(), "Failed to load user.", Toast.LENGTH_SHORT).show()
                return@addSnapshotListener
            }

            val fullName = doc?.getString("fullName") ?: "User"
            binding.tvWelcome.text = "Welcome back, $fullName!"
        }
    }

    /** --- 4️⃣ Listen to Issues (Citizen / Admin) --- */
    private fun listenToUserIssues() {
        val uid = auth.currentUser?.uid ?: return
        issuesListener?.remove()

        firestore.collection("users").document(uid)
            .get()
            .addOnSuccessListener { userDoc ->
                if (!isAdded || _binding == null) return@addOnSuccessListener

                val isAdmin = userDoc.getString("userType") == "Administrator"
                binding.tvRecentIssues.text = if (isAdmin) "Recent Issues" else "Your Recent Issues"

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
                        Toast.makeText(requireContext(), "Error loading issues.", Toast.LENGTH_SHORT).show()
                        return@addSnapshotListener
                    }

                    val issues = snapshot?.documents?.map { doc ->
                        Issue(
                            id = doc.id,
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
                    } ?: emptyList()

                    updateUI(issues, isAdmin)
                }
            }
            .addOnFailureListener {
                if (isAdded && _binding != null)
                    Toast.makeText(requireContext(), "Failed to load user data.", Toast.LENGTH_SHORT).show()
            }
    }

    /** --- 5️⃣ Update Dashboard + Recycler --- */
    private fun updateUI(issues: List<Issue>, isAdmin: Boolean) {
        if (!isAdded || _binding == null) return

        if (issues.isEmpty()) {
            binding.layoutNoIssues.visibility = View.VISIBLE
            binding.recyclerRecentIssues.visibility = View.GONE
        } else {
            binding.layoutNoIssues.visibility = View.GONE
            binding.recyclerRecentIssues.visibility = View.VISIBLE
            adapter.updateData(issues.take(5))
        }

        val total = issues.size
        val inProgress = issues.count {
            it.status.equals("In Progress", true) || it.status.equals("Under Review", true)
        }
        val resolved = issues.count { it.status.equals("Resolved", true) }

        val reportedLabel = if (isAdmin) "All Reported" else "Reported"
        val inProgressLabel = if (isAdmin) "All In Progress" else "In Progress"
        val resolvedLabel = if (isAdmin) "All Resolved" else "Resolved"

        setDashboardCard(
            binding.cardReported.root,
            total,
            reportedLabel,
            R.drawable.ic_reported,
            requireContext().getColor(R.color.colorReported)
        )
        setDashboardCard(
            binding.cardInProgress.root,
            inProgress,
            inProgressLabel,
            R.drawable.ic_inprogress,
            requireContext().getColor(R.color.colorInProgress)
        )
        setDashboardCard(
            binding.cardResolved.root,
            resolved,
            resolvedLabel,
            R.drawable.ic_resolved,
            requireContext().getColor(R.color.colorResolved)
        )
    }

    /** --- 6️⃣ Helper to Populate Dashboard Card --- */
    private fun setDashboardCard(view: View, count: Int, label: String, iconRes: Int, tintColor: Int) {
        if (!isAdded || _binding == null) return

        val icon = view.findViewById<ImageView>(R.id.imgIcon)
        val tvCount = view.findViewById<TextView>(R.id.tvCount)
        val tvLabel = view.findViewById<TextView>(R.id.tvLabel)

        icon.setImageResource(iconRes)
        ImageViewCompat.setImageTintList(icon, ColorStateList.valueOf(tintColor))
        tvCount.text = count.toString()
        tvLabel.text = label
    }

    /** --- 7️⃣ Clean Up Firestore Listeners --- */
    override fun onDestroyView() {
        super.onDestroyView()
        issuesListener?.remove()
        userListener?.remove()
        issuesListener = null
        userListener = null
        _binding = null
    }
}
