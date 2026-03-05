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
        // English
        "spade" to "Spades",
        "spades" to "Spades",
        "heart" to "Hearts",
        "hearts" to "Hearts",
        "diamond" to "Diamonds",
        "diamonds" to "Diamonds",
        "club" to "Clubs",
        "clubs" to "Clubs",
        // Russian nouns
        "пика" to "Spades",
        "пики" to "Spades",
        "пик" to "Spades",
        "черва" to "Hearts",
        "червы" to "Hearts",
        "черви" to "Hearts",
        "червей" to "Hearts",
        "бубна" to "Diamonds",
        "бубны" to "Diamonds",
        "бубей" to "Diamonds",
        "трефа" to "Clubs",
        "трефы" to "Clubs",
        "треф" to "Clubs",
        "крести" to "Clubs",
        "крестей" to "Clubs",
        // Russian adjective forms (e.g. "бубновый король")
        "пиковый" to "Spades",
        "пиковая" to "Spades",
        "червовый" to "Hearts",
        "червовая" to "Hearts",
        "бубновый" to "Diamonds",
        "бубновая" to "Diamonds",
        "трефовый" to "Clubs",
        "трефовая" to "Clubs",
        "крестовый" to "Clubs",
        "крестовая" to "Clubs"
    )

    private val rankWords = mapOf(
        // English
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
        "king" to 13,
        // Russian base
        "один" to 1,
        "два" to 2,
        "три" to 3,
        "четыре" to 4,
        "пять" to 5,
        "шесть" to 6,
        "семь" to 7,
        "восемь" to 8,
        "девять" to 9,
        "десять" to 10,
        "одиннадцать" to 11,
        "двенадцать" to 12,
        "тринадцать" to 13,
        "туз" to 1,
        "валет" to 11,
        "дама" to 12,
        "король" to 13,
        // Russian colloquial rank forms
        "двойка" to 2,
        "тройка" to 3,
        "четверка" to 4,
        "четвёрка" to 4,
        "пятерка" to 5,
        "пятёрка" to 5,
        "шестерка" to 6,
        "шестёрка" to 6,
        "семерка" to 7,
        "семёрка" to 7,
        "восьмерка" to 8,
        "восьмёрка" to 8,
        "девятка" to 9,
        "десятка" to 10
    )

    private val triggerWords = setOf(
        "magic", "magical", "magically",
        "магия", "магический", "магически"
    )

    private val normalizationMap = mapOf(
        // English trigger mishears
        "magik" to "magic",
        "majic" to "magic",
        // English suit near forms
        "clove" to "clubs",
        "harts" to "hearts",
        "spire" to "spades"
    )

    fun parseTranscript(transcript: String, enableNormalization: Boolean): CardParseResult {
        val tokens = transcript.lowercase(Locale.ROOT)
            .split(Regex("[^\\p{L}\\p{Nd}]+"))
            .filter { it.isNotBlank() }
            .map { token -> if (enableNormalization) normalizationMap[token] ?: token else token }

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
