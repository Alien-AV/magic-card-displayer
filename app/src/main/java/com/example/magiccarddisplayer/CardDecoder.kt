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

data class CardParseResult(
    val rankValue: Int?,
    val suitName: String?,
    val hasTriggerWord: Boolean
)

object CardDecoder {
    private val suitNameToSymbol = mapOf(
        "Spades" to "♠",
        "Hearts" to "♥",
        "Diamonds" to "♦",
        "Clubs" to "♣"
    )

    private val suitWords = mapOf(
        "spade" to "Spades",
        "spades" to "Spades",
        "heart" to "Hearts",
        "hearts" to "Hearts",
        "diamond" to "Diamonds",
        "diamonds" to "Diamonds",
        "club" to "Clubs",
        "clubs" to "Clubs",
        // Legacy cue words kept as fallback.
        "shuffles" to "Spades",
        "times" to "Hearts",
        "cuts" to "Diamonds",
        "tricks" to "Clubs"
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

    private val triggerWords = setOf("magic", "magical", "magically")

    fun parseTranscript(transcript: String): CardParseResult {
        val tokens = transcript.lowercase(Locale.US)
            .split(Regex("[^a-z0-9]+"))
            .filter { it.isNotBlank() }

        var lastRank: Int? = null
        var lastSuit: String? = null

        for (token in tokens) {
            parseRank(token)?.let { lastRank = it }
            suitWords[token]?.let { lastSuit = it }
        }

        val hasTrigger = tokens.any { it in triggerWords }
        return CardParseResult(rankValue = lastRank, suitName = lastSuit, hasTriggerWord = hasTrigger)
    }

    fun buildCard(rankValue: Int, suitName: String): DecodedCard {
        val symbol = suitNameToSymbol[suitName] ?: "♣"
        return DecodedCard(
            rankValue = rankValue,
            rankLabel = rankToLabel(rankValue),
            suitSymbol = symbol,
            suitName = suitName
        )
    }

    fun inverse(card: DecodedCard): DecodedCard {
        val inverseRank = 14 - card.rankValue
        val inverseSuit = when (card.suitName) {
            "Spades" -> "Hearts"
            "Hearts" -> "Spades"
            "Clubs" -> "Diamonds"
            "Diamonds" -> "Clubs"
            else -> "Clubs"
        }
        return buildCard(inverseRank, inverseSuit)
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
