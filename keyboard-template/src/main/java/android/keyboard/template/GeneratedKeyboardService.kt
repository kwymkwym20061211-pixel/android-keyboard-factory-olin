package android.keyboard.template

import android.graphics.Bitmap
import android.inputmethodservice.InputMethodService
import android.view.View
import android.keyboard.engine.KeyDef
import android.keyboard.engine.KeyImageProvider
import android.keyboard.engine.KeyRole
import android.keyboard.engine.KeyboardGridView
import android.keyboard.engine.KeyboardLayout
import android.keyboard.engine.LayoutJson

private const val LAYOUT_ASSET = "keyboard_layout.json"
private const val IMAGE_ASSET_DIR = "key_images"

class GeneratedKeyboardService : InputMethodService() {

    private lateinit var layout: KeyboardLayout
    private var pageIndex = 0
    private lateinit var gridView: KeyboardGridView

    override fun onCreate() {
        super.onCreate()
        layout = assets.open(LAYOUT_ASSET).use { it.reader().readText() }.let(LayoutJson::decode)
    }

    override fun onCreateInputView(): View {
        gridView = KeyboardGridView(this).apply {
            imageProvider = AssetKeyImageProvider()
            onKeyTapped = ::handleKeyTapped
        }
        renderCurrentPage()
        return FixedHeightContainer(this, resources.getDimensionPixelSize(R.dimen.keyboard_height)).apply {
            setContent(gridView)
        }
    }

    // A custom IME view has no inherent size hint for the framework, so without this it can be
    // stretched to fill the whole screen instead of a normal keyboard-sized strip at the bottom.
    override fun onEvaluateFullscreenMode(): Boolean = false

    private fun renderCurrentPage() {
        gridView.setPage(layout.pages[pageIndex])
    }

    private fun handleKeyTapped(key: KeyDef) {
        val ic = currentInputConnection ?: return
        when (key.role) {
            KeyRole.CHAR -> ic.commitText(key.text.orEmpty(), 1)
            KeyRole.ENTER -> ic.commitText("\n", 1)
            KeyRole.DELETE -> ic.deleteSurroundingText(1, 0)
            KeyRole.PAGE_NEXT -> {
                pageIndex = (pageIndex + 1).coerceAtMost(layout.pages.size - 1)
                renderCurrentPage()
            }
            KeyRole.PAGE_PREV -> {
                pageIndex = (pageIndex - 1).coerceAtLeast(0)
                renderCurrentPage()
            }
            KeyRole.NONE -> Unit
        }
    }

    private inner class AssetKeyImageProvider : KeyImageProvider {
        override fun getBitmap(imagePath: String): Bitmap? = runCatching {
            assets.open("$IMAGE_ASSET_DIR/$imagePath").use { android.graphics.BitmapFactory.decodeStream(it) }
        }.getOrNull()
    }
}
