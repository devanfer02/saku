package com.dvnnfrr.saku

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Local-only means the ledger never leaves the phone unless the user moves a file. With
 * `allowBackup` on, Android's own cloud backup would copy `saku.db` off the device, so
 * it stays off; Saku's backup is the file the user writes from More.
 */
class NoCloudBackupTest {

    private fun manifest(): String {
        val file = File("src/main/AndroidManifest.xml")
        assertTrue("Expected to find ${file.absolutePath}", file.exists())
        return file.readText()
    }

    @Test
    fun `android cloud backup is off`() {
        val text = manifest()
        assertTrue("allowBackup must be false", text.contains("android:allowBackup=\"false\""))
        assertFalse("no backup rules once backup is off", text.contains("android:fullBackupContent"))
        assertFalse("no extraction rules once backup is off", text.contains("android:dataExtractionRules"))
    }
}
