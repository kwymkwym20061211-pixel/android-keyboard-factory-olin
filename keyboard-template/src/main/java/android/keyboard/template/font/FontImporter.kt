package android.keyboard.template.font

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

/** Imports a SAF-picked font file into this app's private storage so later rendering never
 * depends on a content:// Uri whose grant could be revoked. Only one custom font is supported
 * here at a time (unlike `:app`'s per-project/per-page FontImporter), so [import] always wipes
 * the fonts dir first -- otherwise replacing a font picked under a different file name, or
 * clearing one, would leave the old file orphaned on disk. Mirrors `:app`'s FontImporter
 * (`:keyboard-template` can't depend on `:app`, so this is a small from-scratch equivalent). */
object FontImporter {
    private const val FONTS_DIR_NAME = "fonts"

    fun displayName(context: Context, uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }

    fun import(context: Context, uri: Uri, fileName: String): File {
        clear(context)
        val dest = File(fontsDir(context), fileName)
        val input = context.contentResolver.openInputStream(uri) ?: error("Could not open font: $uri")
        input.use { stream -> dest.outputStream().use { output -> stream.copyTo(output) } }
        return dest
    }

    fun clear(context: Context) {
        fontsDir(context).listFiles()?.forEach { it.delete() }
    }

    fun currentFont(context: Context): File? = fontsDir(context).listFiles()?.firstOrNull()

    private fun fontsDir(context: Context): File = File(context.filesDir, FONTS_DIR_NAME).apply { mkdirs() }
}
