package com.example.magiccardtv

import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.core.content.ContextCompat
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

class IdleBackgroundRepository(private val target: ImageView) {
    private val client = OkHttpClient()
    private val cacheFile = File(target.context.cacheDir, "idle-background.jpg")

    fun showCachedOrFallback() {
        val drawable = readCachedDrawable()
        if (drawable != null) {
            target.setImageDrawable(drawable)
        } else {
            target.setImageDrawable(ContextCompat.getDrawable(target.context, android.R.color.black))
        }
    }

    fun updateFromUrl(url: String, onStatus: (String) -> Unit) {
        Thread {
            try {
                val response = client.newCall(Request.Builder().url(url).build()).execute()
                if (!response.isSuccessful) {
                    onStatus("Idle image download failed (${response.code})")
                    return@Thread
                }
                val bytes = response.body?.bytes() ?: return@Thread
                cacheFile.writeBytes(bytes)
                target.post {
                    showCachedOrFallback()
                    onStatus("Idle image updated")
                }
            } catch (ex: Exception) {
                target.post {
                    onStatus("Idle image error; keeping cached")
                    showCachedOrFallback()
                }
            }
        }.start()
    }

    private fun readCachedDrawable(): Drawable? {
        if (!cacheFile.exists()) return null
        val bmp = BitmapFactory.decodeFile(cacheFile.absolutePath) ?: return null
        return BitmapDrawable(target.resources, bmp)
    }
}
