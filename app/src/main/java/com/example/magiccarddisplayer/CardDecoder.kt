package com.example.magiccarddisplayer

import java.util.Locale

data class DecodedCard(
    val rankValue: Int,
    val rankLabel: String,
    val suitSymbol: String,
    val suitName: String
) {
    val display: String = "$rankLabel$suitSymbol"
}

object CardDecoder {
    private val suitMap = mapOf(
        "shuffles" to ("♠" to "Spades"),
        "times" to ("♥" to "Hearts"),
        "cuts" to ("♦" to "Diamonds"),
        "tricks" to ("♣" to "Clubs")
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

    fun decode(transcript: String): DecodedCard? {
        val normalized = transcript.lowercase(Locale.US)
        val tokens = normalized.split(Regex("[^a-z0-9]+"))
            .filter { it.isNotBlank() }

        if (!tokens.contains("magical")) return null

        val suitEntry = tokens.firstNotNullOfOrNull { token ->
            suitMap[token]?.let { (symbol, name) -> token to (symbol to name) }
        } ?: return null

        val rankValue = tokens.firstNotNullOfOrNull { token -> parseRank(token) } ?: return null

        return DecodedCard(
            rankValue = rankValue,
            rankLabel = rankToLabel(rankValue),
            suitSymbol = suitEntry.second.first,
            suitName = suitEntry.second.second
        )
    }

    private fun parseRank(token: String): Int? {
        token.toIntOrNull()?.let { if (it in 1..13) return it }
        return rankWords[token]
    }

    private fun rankToLabel(rank: Int): String {
        return when (rank) {
            1 -> "A"
            11 -> "J"
            12 -> "Q"
            13 -> "K"
            else -> rank.toString()
        }
    }
}
