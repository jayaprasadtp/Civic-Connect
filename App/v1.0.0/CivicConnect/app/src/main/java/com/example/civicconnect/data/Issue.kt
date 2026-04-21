package com.example.civicconnect.data

data class Issue(
    val id: String = "",
    val ownerUid: String = "",
    val title: String = "",
    val category: String = "",
    val description: String = "",
    val location: String = "",
    val imageUri: String? = null,        // download URL
    val status: String = "Pending",
    val trackingNumber: String = "",
    val dateReported: Long = 0L,
    val priorityScore: Double = 0.0,     // set by AI (0..1 or any scale)
    val duplicateOf: String? = null,     // docId if duplicate
    val remarksCount: Int = 0,           // kept for a quick badge
    val docPath: String = "",            // "users/{uid}/issues/{issueId}" for admin updates
    val userType: String = ""            // optional (not used in admin lists)
)
