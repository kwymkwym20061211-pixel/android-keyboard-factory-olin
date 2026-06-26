package android.keyboard.engine

import android.graphics.Bitmap

/** Decouples [KeyboardGridView] from where CHAR-key glyph bitmaps actually come from
 * (editor-side render cache vs. assets shipped inside a generated keyboard apk). */
interface KeyImageProvider {
    fun getBitmap(imagePath: String): Bitmap?
}
