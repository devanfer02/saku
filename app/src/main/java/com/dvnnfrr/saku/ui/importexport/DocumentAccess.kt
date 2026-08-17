package com.dvnnfrr.saku.ui.importexport

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import java.io.InputStream
import java.io.OutputStream

/*
 * Files reach the app only through the Storage Access Framework.
 *
 * That is not a style preference. Saku ships with no permissions at all — a Gradle task
 * fails the build if one reaches the merged manifest — so there is no READ_EXTERNAL_STORAGE
 * to fall back on and no network to upload to. The user picks a document, the system
 * grants access to that one document, and the app never sees a path.
 *
 * The two interfaces exist so the import and export view models can be driven by a byte
 * array in a unit test. Everything below them is Android; everything above them is not.
 */

/** A document the user chose to read. */
interface DocumentSource {
    val name: String

    fun openInput(): InputStream
}

/** A document the user chose to write. */
interface DocumentSink {
    val name: String

    fun openOutput(): OutputStream
}

class ContentDocumentSource(
    private val resolver: ContentResolver,
    private val uri: Uri,
) : DocumentSource {

    override val name: String = resolver.displayName(uri)

    override fun openInput(): InputStream =
        resolver.openInputStream(uri) ?: error("The picker returned a document that cannot be read")
}

class ContentDocumentSink(
    private val resolver: ContentResolver,
    private val uri: Uri,
) : DocumentSink {

    override val name: String = resolver.displayName(uri)

    override fun openOutput(): OutputStream =
        resolver.openOutputStream(uri) ?: error("The picker returned a document that cannot be written")
}

/**
 * The name the user will recognise, which is not the URI's last path segment — a
 * document picked from Drive or Downloads has an opaque id there. Falling back to the
 * segment is better than showing nothing when a provider declines the query.
 */
private fun ContentResolver.displayName(uri: Uri): String {
    val queried = runCatching {
        query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (column >= 0 && cursor.moveToFirst()) cursor.getString(column) else null
        }
    }.getOrNull()
    return queried?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment.orEmpty()
}

/** The MIME types the picker filters on. Some providers report the generic one. */
val SPREADSHEET_MIME_TYPES = arrayOf(
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "application/octet-stream",
)

const val XLSX_MIME_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
