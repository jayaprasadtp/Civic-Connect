package com.example.civicconnect.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.civicconnect.R
import com.example.civicconnect.data.Issue
import java.text.SimpleDateFormat
import java.util.*

class IssueAdapter(
    private var issues: List<Issue>,
    private val onIssueClick: (Issue) -> Unit
) : RecyclerView.Adapter<IssueAdapter.IssueViewHolder>() {

    inner class IssueViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvTitle: TextView = itemView.findViewById(R.id.tvIssueTitle)
        val tvStatus: TextView = itemView.findViewById(R.id.tvStatusBadge)
        val tvTracking: TextView = itemView.findViewById(R.id.tvTrackingNumber)
        val tvCategory: TextView = itemView.findViewById(R.id.tvCategory)
        val tvDate: TextView = itemView.findViewById(R.id.tvDate)
        val card: CardView = itemView.findViewById(R.id.cardView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IssueViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_issue_card, parent, false)
        return IssueViewHolder(view)
    }

    override fun onBindViewHolder(holder: IssueViewHolder, position: Int) {
        val issue = issues[position]
        val context = holder.itemView.context

        holder.tvTitle.text = issue.title
        holder.tvTracking.text = "#${issue.trackingNumber}"
        holder.tvCategory.text = issue.category
        holder.tvDate.text = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
            .format(Date(issue.dateReported))
        holder.tvStatus.text = issue.status

        // Hide priority for user side
        holder.itemView.findViewById<TextView?>(R.id.tvPriority)?.visibility = View.GONE

        // 🎨 Dynamic badge color using XML resources
        val colorRes = when (issue.status.lowercase()) {
            "pending" -> R.color.colorDefault
            "under review" -> R.color.colorInProgress
            "in progress" -> R.color.colorReported
            "resolved" -> R.color.colorResolved
            "rejected" -> R.color.priority_high
            else -> R.color.colorDefault
        }

        holder.tvStatus.setBackgroundColor(ContextCompat.getColor(context, colorRes))
        holder.tvStatus.setTextColor(ContextCompat.getColor(context, R.color.white))

        // ✅ Handle click
        holder.card.setOnClickListener { onIssueClick(issue) }
    }

    override fun getItemCount() = issues.size

    fun updateData(newList: List<Issue>) {
        issues = newList
        notifyDataSetChanged()
    }
}
