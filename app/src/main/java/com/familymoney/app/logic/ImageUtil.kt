package com.familymoney.app.logic

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class CompressedImage(val base64: String, val preview: Bitmap)

object ImageUtil {

    /** Shrinks a photo to about 1280 px (keeps it upright using the camera's EXIF rotation) and returns it as base64 JPEG. */
    suspend fun compress(context: Context, uri: Uri, maxDim: Int = 1280, quality: Int = 72): CompressedImage =
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("That file is not a usable photo.")

            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / sample > maxDim * 2) sample *= 2
            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sample }
            var bitmap: Bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, decodeOptions) }
                ?: throw IOException("Could not read the photo.")

            val orientation = try {
                resolver.openInputStream(uri)?.use {
                    ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                } ?: ExifInterface.ORIENTATION_NORMAL
            } catch (e: Exception) {
                ExifInterface.ORIENTATION_NORMAL
            }
            val rotation = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            if (rotation != 0f) {
                val m = Matrix()
                m.postRotate(rotation)
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
            }

            val scale = min(1f, maxDim.toFloat() / max(bitmap.width, bitmap.height))
            if (scale < 1f) {
                bitmap = Bitmap.createScaledBitmap(
                    bitmap, (bitmap.width * scale).roundToInt(), (bitmap.height * scale).roundToInt(), true
                )
            }

            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            CompressedImage(Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP), bitmap)
        }

    fun decodeBase64(b64: String): Bitmap? {
        return try {
            val bytes = Base64.decode(b64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            null
        }
    }
}
