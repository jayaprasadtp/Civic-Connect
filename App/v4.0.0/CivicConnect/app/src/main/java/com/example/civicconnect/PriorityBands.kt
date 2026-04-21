package com.example.civicconnect

import androidx.annotation.ColorRes

data class PriorityBand(
    val label: String,
    @param:ColorRes val colorRes: Int
)

object PriorityBands {
    const val HIGH_MIN_SCORE = 0.75
    const val MEDIUM_MIN_SCORE = 0.40

    fun fromScore(score: Double): PriorityBand {
        val normalizedScore = score.coerceIn(0.0, 1.0)

        return when {
            normalizedScore >= HIGH_MIN_SCORE -> PriorityBand("High", R.color.priority_high)
            normalizedScore >= MEDIUM_MIN_SCORE -> PriorityBand("Medium", R.color.priority_medium)
            else -> PriorityBand("Low", R.color.priority_low)
        }
    }
}
