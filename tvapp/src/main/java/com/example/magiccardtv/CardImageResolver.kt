package com.example.magiccardtv

import android.content.Context

object CardImageResolver {
    fun resourceForCode(context: Context, code: String): Int {
        val normalized = code.lowercase().trim()
        if (!normalized.matches(Regex("^(a|[2-9]|10|j|q|k)[shdc]$"))) {
            return 0
        }
        return context.resources.getIdentifier("card_$normalized", "drawable", context.packageName)
    }
}
