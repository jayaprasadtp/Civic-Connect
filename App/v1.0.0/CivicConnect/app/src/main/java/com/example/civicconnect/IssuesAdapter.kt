package com.example.civicconnect

import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.example.civicconnect.R
import com.example.civicconnect.data.Issue

class IssuesAdapter(
    private var issues: List<Issue>,
    private val onItemClick: (Issue) -> Unit  // ✅ click listener lambda
) : RecyclerView.Adapter<IssuesAdapter.IssueViewHolder>() {

    inner class IssueViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.tvIssueTitle)
        val status: TextView = view.findViewById(R.id.tvIssueStatus)
        val date: TextView = view.findViewById(R.id.tvIssueDate)
        val card: CardView? = view.findViewById(R.id.cardView) // optional if using CardView
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IssueViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_issue, parent, false)
        return IssueViewHolder(view)
    }

    override fun getItemCount(): Int = issues.size

    override fun onBindViewHolder(holder: IssueViewHolder, position: Int) {
        val issue = issues[position]

        holder.title.text = issue.title
        holder.status.text = issue.status
        holder.date.text = DateFormat.format("dd MMM yyyy", issue.dateReported)

        // ✅ handle clicks
        holder.itemView.setOnClickListener {
            onItemClick(issue)
        }
    }

    fun updateData(newList: List<Issue>) {
        issues = newList
        notifyDataSetChanged()
    }
}
