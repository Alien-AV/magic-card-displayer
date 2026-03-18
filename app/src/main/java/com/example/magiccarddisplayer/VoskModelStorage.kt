package com.example.magiccarddisplayer

import android.content.Context
import java.io.File

object VoskModelStorage {
    private const val MODEL_ASSET_DIR = "model-en-us"

    fun ensureEnglishModelCopied(context: Context): File? {
        val targetDir = File(context.filesDir, "vosk/$MODEL_ASSET_DIR")
        val markerFile = File(targetDir, "am/final.mdl")
        if (markerFile.exists()) {
            return targetDir
        }

        val assets = context.assets
        return try {
            copyAssetDirectory(assets, MODEL_ASSET_DIR, targetDir)
            if (markerFile.exists()) targetDir else null
        } catch (_: Exception) {
            null
        }
    }

    private fun copyAssetDirectory(assetManager: android.content.res.AssetManager, assetPath: String, targetDir: File) {
        val entries = assetManager.list(assetPath).orEmpty()
        if (entries.isEmpty()) {
            targetDir.parentFile?.mkdirs()
            assetManager.open(assetPath).use { input ->
                targetDir.outputStream().use { output -> input.copyTo(output) }
            }
            return
        }

        targetDir.mkdirs()
        entries.forEach { name ->
            copyAssetDirectory(assetManager, "$assetPath/$name", File(targetDir, name))
        }
    }
}
