package android.keyboard.template.dictionary.csv

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns

/** Thin platform-IO wrapper around SAF/MediaStore for dictionary CSV files. Kept separate from
 * the pure [DictionaryCsv] encode/decode logic so that logic stays unit-testable without a
 * Context. Mirrors `:app`'s `DownloadsWriter`/`FontImporter` pattern (`:keyboard-template` can't
 * depend on `:app`'s module directly, so this is a small from-scratch equivalent). */
object DictionaryCsvIo {

    fun displayName(context: Context, uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }

    fun readText(context: Context, uri: Uri): String =
        context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: error("Could not open file: $uri")

    fun writeToDownloads(context: Context, displayName: String, content: String): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, displayName)
            put(MediaStore.Downloads.MIME_TYPE, "text/csv")
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/keyboard")
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("Failed to create a Downloads entry")
        resolver.openOutputStream(uri)?.use { out -> out.write(content.toByteArray(Charsets.UTF_8)) }
            ?: error("Failed to open Downloads entry for writing")
        return uri
    }
}
