package com.example.civicconnect.data

data class Issue(
    val id: String = "",
    val ownerUid: String = "",
    val title: String = "",
    val category: String = "",
    val description: String = "",
    val location: String = "",
    val imageUri: String? = null,
    val status: String = "Pending",
    val trackingNumber: String = "",
    val dateReported: Long = 0L,
    val priorityScore: Double = 0.0,
    val duplicateOf: String? = null,
    val duplicateTrackingNumber: String? = null,
    val similarityScore: Double? = null,
    val remarksCount: Int = 0,
    val docPath: String = "",
    val userType: String = ""
)