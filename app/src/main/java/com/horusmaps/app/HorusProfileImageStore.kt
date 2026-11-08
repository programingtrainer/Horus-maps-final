package com.horusmaps.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest

object HorusProfileImageStore {
    suspend fun encodeJpeg(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            } ?: return@runCatching null
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1024) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            val decoded = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            } ?: return@runCatching null

            val maxSide = maxOf(decoded.width, decoded.height)
            val bitmap = if (maxSide > 512) {
                val scale = 512f / maxSide
                Bitmap.createScaledBitmap(
                    decoded,
                    (decoded.width * scale).toInt().coerceAtLeast(1),
                    (decoded.height * scale).toInt().coerceAtLeast(1),
                    true
                ).also { if (it !== decoded) decoded.recycle() }
            } else decoded

            val output = ByteArrayOutputStream()
            var quality = 84
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)
            while (output.size() > 260_000 && quality > 58) {
                output.reset()
                quality -= 6
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)
            }
            if (bitmap !== decoded || !decoded.isRecycled) bitmap.recycle()
            if (output.size() > 300_000) return@runCatching null
            Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
        }.getOrNull()
    }

    fun saveBase64(context: Context, email: String, imageData: String): String? =
        runCatching {
            val encoded = imageData.substringAfter("base64,", imageData)
            val bytes = Base64.decode(encoded, Base64.DEFAULT)
            val directory = File(context.filesDir, "horus-profile-images").apply { mkdirs() }
            val file = File(directory, fileName(email))
            file.writeBytes(bytes)
            Uri.fromFile(file).toString()
        }.getOrNull()

    fun deleteLocal(context: Context, email: String) {
        runCatching { File(File(context.filesDir, "horus-profile-images"), fileName(email)).delete() }
    }

    private fun fileName(email: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(email.trim().lowercase().toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) } + ".jpg"
    }
}
