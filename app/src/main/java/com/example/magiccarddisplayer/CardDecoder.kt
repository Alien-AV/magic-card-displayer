package com.example.magiccarddisplayer

data class DecodedCard(
    val rankValue: Int,
    val suitSymbol: String
) {
    val displayRank: String
        get() = when (rankValue) {
            1 -> "A"
            11 -> "J"
            12 -> "Q"
            13 -> "K"
            else -> rankValue.toString()
        }

    val display: String
        get() = "$displayRank$suitSymbol"
}

object CardDecoder {
    private val suitMap = mapOf(
        "shuffles" to "♠",
        "times" to "♥",
        "cuts" to "♦",
        "tricks" to "♣"
    )

    private val rankWordMap = mapOf(
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
        "thirteen" to 13
    )

    fun decode(transcript: String): DecodedCard? {
        val normalized = transcript
            .lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .trim()
        if (normalized.isEmpty()) return null

        val words = normalized.split(Regex("\\s+")).filter { it.isNotBlank() }
        if ("magical" !in words) return null

        val suitSymbol = words.firstNotNullOfOrNull { suitMap[it] } ?: return null
        val rank = extractRank(words) ?: return null

        return DecodedCard(rank, suitSymbol)
    }

    private fun extractRank(words: List<String>): Int? {
        words.forEach { token ->
            token.toIntOrNull()?.let { numeric ->
                if (numeric in 1..13) return numeric
            }
            rankWordMap[token]?.let { return it }
        }
        return null
    }
}
