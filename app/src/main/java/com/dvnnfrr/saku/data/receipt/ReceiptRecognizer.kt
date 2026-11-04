package com.dvnnfrr.saku.data.receipt

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.dvnnfrr.saku.receipt.OcrLine
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Reads the text on a receipt photo or screenshot, on the device.
 *
 * The only file that knows ML Kit. It hands back [OcrLine]s, so the parser in `receipt/`
 * and everything above it work on plain text and boxes and are tested without a device.
 *
 * Camera photos go to `cache/receipts/` and are deleted as soon as they are read; a photo
 * picked from the gallery is read in place and never copied. Nothing from the image is
 * kept beyond what the user saves on the sheet.
 */
class ReceiptRecognizer(private val context: Context) {

    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    private val folder: File get() = File(context.cacheDir, CACHE_FOLDER)

    private val authority: String get() = "${context.packageName}$AUTHORITY_SUFFIX"

    /** A fresh file for the system camera to write into, as a URI it may write to. */
    fun newCameraUri(): Uri {
        folder.mkdirs()
        val file = File(folder, "receipt-${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, authority, file)
    }

    /** The recognized lines, or none if the image could not be decoded or read. */
    suspend fun read(uri: Uri): List<OcrLine> = withContext(Dispatchers.Default) {
        try {
            val bitmap = decode(uri) ?: return@withContext emptyList()
            try {
                recognize(bitmap).toLines()
            } finally {
                bitmap.recycle()
            }
        } finally {
            if (uri.authority == authority) clearCache()
        }
    }

    /** Removes camera photos left behind by a read that never finished. */
    fun clearCache() {
        folder.listFiles()?.forEach { it.delete() }
    }

    /*
     * Downscaled so the long side is at most MAX_SIDE: a 12 MP photo took over 3 s to read
     * in the spike, and receipt text stays legible well below that. Software bitmaps only,
     * since ML Kit cannot read a hardware one.
     */
    private fun decode(uri: Uri): Bitmap? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val scale = MAX_SIDE.toFloat() / maxOf(info.size.width, info.size.height)
                if (scale < 1f) {
                    decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
                }
            }
        } else {
            // API 26 and 27: no ImageDecoder, and no EXIF rotation either. ML Kit still
            // reads rotated text, only a little less reliably.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        }
    }.getOrNull()

    private suspend fun recognize(bitmap: Bitmap): Text = suspendCancellableCoroutine { continuation ->
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { continuation.resume(it) }
            .addOnFailureListener { continuation.resumeWithException(it) }
    }

    private fun Text.toLines(): List<OcrLine> = textBlocks.flatMap { it.lines }.mapNotNull { line ->
        val box = line.boundingBox ?: return@mapNotNull null
        OcrLine(line.text, box.left, box.top, box.right, box.bottom)
    }

    private companion object {
        const val CACHE_FOLDER = "receipts"
        const val AUTHORITY_SUFFIX = ".receipts"
        const val MAX_SIDE = 2_000
    }
}
