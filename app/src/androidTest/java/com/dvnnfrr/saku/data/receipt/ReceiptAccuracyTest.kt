package com.dvnnfrr.saku.data.receipt

import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Reads every image in `/data/local/tmp/receipts/` through [ReceiptRecognizer] and writes
 * the recognized lines to the app's `files/receipt-ocr/<name>.txt`, in the dump format of
 * the JVM fixtures (`left,top,right,bottom<TAB>text`, one line each). Skipped when the
 * folder is empty, so a normal `connectedAndroidTest` run passes without images.
 *
 * ```
 * adb push data/receipt-long/. /data/local/tmp/receipts/
 * adb shell am instrument -w -e class com.dvnnfrr.saku.data.receipt.ReceiptAccuracyTest \
 *     com.dvnnfrr.saku.test/androidx.test.runner.AndroidJUnitRunner
 * adb exec-out run-as com.dvnnfrr.saku cat files/receipt-ocr/<name>.txt > <fixture>.txt
 * ```
 *
 * The JVM tests then measure the parser on those fixtures with recognition held fixed.
 */
@RunWith(AndroidJUnit4::class)
class ReceiptAccuracyTest {

    @Test
    fun dumpRecognizedLines() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext

        // The app may not read what adb pushes to /data/local/tmp, but the shell may.
        fun shell(command: String): ByteArray = ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand(command),
        ).use { it.readBytes() }

        val names = String(shell("ls $INPUT")).lines().map(String::trim)
            .filter { it.substringAfterLast('.').lowercase() in IMAGE_TYPES }
            .sorted()
        assumeTrue("no images in $INPUT", names.isNotEmpty())

        val scratch = File(context.cacheDir, "receipt-accuracy").apply { mkdirs() }
        val output = File(context.filesDir, "receipt-ocr").apply { mkdirs() }
        val recognizer = ReceiptRecognizer(context)
        for (name in names) {
            val image = File(scratch, name)
            image.writeBytes(shell("cat $INPUT/$name"))
            try {
                val lines = runBlocking { recognizer.read(Uri.fromFile(image)) }
                File(output, name.substringBeforeLast('.') + ".txt").writeText(
                    lines.joinToString("\n") { "${it.left},${it.top},${it.right},${it.bottom}\t${it.text}" },
                )
            } finally {
                image.delete()
            }
        }
    }

    private companion object {
        const val INPUT = "/data/local/tmp/receipts"
        val IMAGE_TYPES = setOf("jpg", "jpeg", "png")
    }
}
