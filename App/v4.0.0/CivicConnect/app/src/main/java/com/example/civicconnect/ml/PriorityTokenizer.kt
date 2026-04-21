package com.example.civicconnect.ml

import android.content.Context
import java.util.Locale

object PriorityTokenizer {
    const val MAX_SEQUENCE_LENGTH = 96
    const val MODEL_ASSET_PATH = "ml/priority_mobile_regressor.tflite"
    const val VOCAB_ASSET_PATH = "ml/vocabulary.txt"

    private const val OOV_TOKEN_ID = 1

    private val invalidChars = Regex("[^a-z0-9:_ ]+")
    private val multiSpace = Regex("\\s+")

    fun buildModelText(title: String, description: String, category: String): String {
        return "title: $title category: $category description: $description important_title: $title"
    }

    fun normalize(raw: String): String {
        return raw
            .lowercase(Locale.US)
            .replace(invalidChars, " ")
            .replace(multiSpace, " ")
            .trim()
    }

    fun loadVocabulary(context: Context): Map<String, Int> {
        val vocabulary = linkedMapOf<String, Int>()

        context.assets.open(VOCAB_ASSET_PATH).bufferedReader().use { reader ->
            reader.readLines().forEachIndexed { index, token ->
                vocabulary[token] = index
            }
        }

        require(vocabulary.isNotEmpty()) { "Priority vocabulary is empty." }
        return vocabulary
    }

    fun encode(
        title: String,
        description: String,
        category: String,
        vocabulary: Map<String, Int>
    ): IntArray {
        val normalized = normalize(buildModelText(title, description, category))
        val tokens = normalized.split(" ").filter { it.isNotBlank() }
        val ids = IntArray(MAX_SEQUENCE_LENGTH)
        val limit = minOf(tokens.size, MAX_SEQUENCE_LENGTH)

        for (index in 0 until limit) {
            ids[index] = vocabulary[tokens[index]] ?: OOV_TOKEN_ID
        }

        return ids
    }
}
