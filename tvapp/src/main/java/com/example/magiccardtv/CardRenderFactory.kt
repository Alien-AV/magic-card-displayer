package com.example.magiccardtv

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF

object CardRenderFactory {
    fun create(rank: String, suitSymbol: String, suitName: String, width: Int = 1080, height: Int = 1620): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val suitColor = if (suitName == "Hearts" || suitName == "Diamonds") Color.parseColor("#D32F2F") else Color.parseColor("#111111")

        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#DDDDDD")
            style = Paint.Style.STROKE
            strokeWidth = 12f
        }
        val rect = RectF(20f, 20f, width - 20f, height - 20f)
        canvas.drawRoundRect(rect, 48f, 48f, bg)
        canvas.drawRoundRect(rect, 48f, 48f, border)

        val corner = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = suitColor
            textSize = 130f
            isFakeBoldText = true
            textAlign = Paint.Align.LEFT
        }
        canvas.drawText(rank, 80f, 170f, corner)
        canvas.drawText(suitSymbol, 80f, 295f, corner)

        corner.textAlign = Paint.Align.RIGHT
        canvas.drawText(rank, width - 80f, height - 160f, corner)
        canvas.drawText(suitSymbol, width - 80f, height - 35f, corner)

        val center = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = suitColor
            textSize = 560f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(suitSymbol, width / 2f, height / 2f + 200f, center)

        return bitmap
    }
}
