package android.keyboard.factory.olin.icon

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import java.io.File

/** Copies a picked image's bytes into this app's private storage immediately on import, center-
 * cropped to a square whose side is the shorter of the image's width/height (i.e. cropping as
 * little as possible), the same way [android.keyboard.factory.olin.font.FontImporter] copies
 * fonts out of their content:// Uri so later use never depends on a revocable grant. */
object IconImporter {

    private const val MAX_STORED_SIDE = 512

    fun import(context: Context, uri: Uri, fileName: String): File {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        val decoded = ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }

        val side = minOf(decoded.width, decoded.height)
        val cropped = Bitmap.createBitmap(decoded, (decoded.width - side) / 2, (decoded.height - side) / 2, side, side)
        val squared = if (side > MAX_STORED_SIDE) {
            Bitmap.createScaledBitmap(cropped, MAX_STORED_SIDE, MAX_STORED_SIDE, true)
        } else {
            cropped
        }

        val iconsDir = File(context.filesDir, "icons").apply { mkdirs() }
        val dest = File(iconsDir, fileName)
        dest.outputStream().use { output -> squared.compress(Bitmap.CompressFormat.PNG, 100, output) }
        return dest
    }
}
