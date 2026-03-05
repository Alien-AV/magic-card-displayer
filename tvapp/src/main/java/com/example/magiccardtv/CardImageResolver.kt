package com.example.magiccardtv

import android.content.Context

object CardImageResolver {
    fun assetUriForCode(context: Context, code: String): String? {
        val normalized = code.lowercase().trim()
        val match = Regex("^(a|[2-9]|10|j|q|k)([shdc])$").matchEntire(normalized) ?: return null

        val rankToken = match.groupValues[1]
        val suitToken = match.groupValues[2]

        val rankName = when (rankToken) {
            "a" -> "ace"
            "j" -> "jack"
            "q" -> "queen"
            "k" -> "king"
            else -> rankToken
        }
        val suitName = when (suitToken) {
            "s" -> "spades"
            "h" -> "hearts"
            "d" -> "diamonds"
            else -> "clubs"
        }

        val fileName = "${rankName}_of_${suitName}.png"
        val assetPath = "cards/$fileName"
        return try {
            context.assets.open(assetPath).close()
            "file:///android_asset/$assetPath"
        } catch (_: Exception) {
            null
        }
    }

    fun resourceForCode(context: Context, code: String): Int {
        val normalized = code.lowercase().trim()
        if (!normalized.matches(Regex("^(a|[2-9]|10|j|q|k)[shdc]$"))) {
            return 0
        }
        return context.resources.getIdentifier("card_$normalized", "drawable", context.packageName)
    }
}
