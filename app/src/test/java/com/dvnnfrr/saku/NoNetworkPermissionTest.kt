package com.dvnnfrr.saku

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Ad-free and offline are structural guarantees, not conventions. This is the fast
 * guard over the hand-written manifest; [app/build.gradle.kts] registers a
 * `check<Variant>NoNetworkPermission` task that inspects the *merged* manifest and
 * so also catches a permission arriving transitively from a dependency.
 */
class NoNetworkPermissionTest {

    private val networkPermissions = listOf(
        "android.permission.INTERNET",
        "android.permission.ACCESS_NETWORK_STATE",
        "android.permission.ACCESS_WIFI_STATE",
    )

    private fun manifest(): String {
        // Unit tests run with the module directory as the working directory.
        val file = File("src/main/AndroidManifest.xml")
        assertTrue("Expected to find ${file.absolutePath}", file.exists())
        return file.readText()
    }

    private val usesPermission = Regex("""<uses-permission[^>]*>""")
    private val permissionName = Regex("""android:name\s*=\s*"([^"]+)"""")

    /** A `tools:node="remove"` entry strips a permission a library merged in; it declares nothing. */
    private fun String.isRemoval(): Boolean = contains("""tools:node="remove"""")

    private fun permissions(removals: Boolean): Set<String> = usesPermission.findAll(manifest())
        .map { it.value }
        .filter { it.isRemoval() == removals }
        .mapNotNull { permissionName.find(it)?.groupValues?.get(1) }
        .toSet()

    @Test
    fun `manifest declares no network permission`() {
        val declared = permissions(removals = false)
        networkPermissions.forEach { permission ->
            assertFalse(
                "$permission must never be declared, see the ad-free guarantee in design.md",
                permission in declared,
            )
        }
    }

    /**
     * ML Kit text recognition (receipt-scan) merges INTERNET and ACCESS_NETWORK_STATE
     * through its logging backend. Dropping these removals would put both in the merged
     * manifest; the merged-manifest check would then fail the build, but this says why.
     */
    @Test
    fun `manifest removes the network permissions ML Kit merges in`() {
        assertEquals(
            setOf("android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE"),
            permissions(removals = true),
        )
    }

    /**
     * The weekly reminder (logging-reminder) needs exactly these two: one to show the
     * notification, one to re-arm it after a restart. Neither reaches the network.
     */
    private val allowed = setOf(
        "android.permission.POST_NOTIFICATIONS",
        "android.permission.RECEIVE_BOOT_COMPLETED",
    )

    @Test
    fun `manifest declares only the allowlisted permissions`() {
        val declared = permissions(removals = false)
        assertEquals(
            "A <uses-permission> outside the allowlist appeared in the manifest; if it is " +
                "genuinely needed, update this test deliberately rather than deleting it",
            allowed,
            declared,
        )
    }
}
