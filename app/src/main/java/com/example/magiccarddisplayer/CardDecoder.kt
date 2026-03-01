package com.example.magiccarddisplayer

import java.util.Locale

data class DecodedCard(
    val rankValue: Int,
    val rankDisplay: String,
    val suitDisplay: String,
    val compactDisplay: String
)

object CardDecoder {
    private val suitMap = mapOf(
        "shuffles" to "♠",
        "times" to "♥",
        "cuts" to "♦",
        "tricks" to "♣"
    )

    private val rankWords = mapOf(
        "one" to 1,
        "two" to 2,
        "three" to 3,
        "four" to 4,
        "five" to 5,
        "six" to 6,
        "seven" to 7,
        "eight" to 8,
        "nine" to 9,
        "ten" to 10,
        "eleven" to 11,
        "twelve" to 12,
        "thirteen" to 13,
        "ace" to 1,
        "jack" to 11,
        "queen" to 12,
        "king" to 13
    )

    fun decode(raw: String): DecodedCard? {
        val normalized = raw.lowercase(Locale.US)
        val tokens = normalized
            .split(Regex("[^a-z0-9]+"))
            .filter { it.isNotBlank() }

        if (!tokens.contains("magical")) {
            return null
        }

        val suit = tokens.firstNotNullOfOrNull { token -> suitMap[token] }
            ?: return null

        val rank = tokens.firstNotNullOfOrNull { token -> parseRankToken(token) }
            ?: return null

        val rankDisplay = when (rank) {
            1 -> "A"
            11 -> "J"
            12 -> "Q"
            13 -> "K"
            else -> rank.toString()
        }

        return DecodedCard(
            rankValue = rank,
            rankDisplay = rankDisplay,
            suitDisplay = suit,
            compactDisplay = "$rankDisplay$suit"
        )
    }

    private fun parseRankToken(token: String): Int? {
        token.toIntOrNull()?.let { numeric ->
            if (numeric in 1..13) {
                return numeric
            }
        }

        return rankWords[token]
    }
}
