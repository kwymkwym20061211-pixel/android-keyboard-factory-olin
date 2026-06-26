package android.keyboard.factory.olin.font

import android.content.Context
import android.net.Uri
import java.io.File

/** Copies a SAF-picked font file's bytes into this app's private storage immediately on import,
 * so later rendering never depends on a content:// Uri whose grant could be revoked or whose
 * backing file could move. */
object FontImporter {
    fun import(context: Context, uri: Uri, fileName: String): File {
        val fontsDir = File(context.filesDir, "fonts").apply { mkdirs() }
        val dest = File(fontsDir, fileName)
        val input = context.contentResolver.openInputStream(uri) ?: error("Could not open font: $uri")
        input.use { stream -> dest.outputStream().use { output -> stream.copyTo(output) } }
        return dest
    }
}
