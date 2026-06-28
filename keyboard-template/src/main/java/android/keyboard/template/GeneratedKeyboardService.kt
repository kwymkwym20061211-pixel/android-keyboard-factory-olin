package android.keyboard.template

import android.graphics.Bitmap
import android.graphics.Typeface
import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.InputConnection
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.keyboard.engine.KeyDef
import android.keyboard.engine.KeyImageProvider
import android.keyboard.engine.KeyRole
import android.keyboard.engine.KeyboardGridView
import android.keyboard.engine.KeyboardLayout
import android.keyboard.engine.LayoutJson
import android.keyboard.template.dictionary.data.DictionaryDatabase
import android.keyboard.template.dictionary.data.DictionaryRepository
import android.keyboard.template.dictionary.data.WordEntity
import android.keyboard.template.font.FontImporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

private const val LAYOUT_ASSET = "keyboard_layout.json"
private const val IMAGE_ASSET_DIR = "key_images"
private const val MAX_CANDIDATES = 20

class GeneratedKeyboardService : InputMethodService() {

    private lateinit var layout: KeyboardLayout
    private var pageIndex = 0
    private lateinit var gridView: KeyboardGridView
    private var candidateContainer: LinearLayout? = null
    private var candidateTypeface: Typeface? = null

    // The "reading" typed so far, not yet committed to the input field — shown as composing text
    // and used to look up dictionary candidates. Cleared on commit/confirm or on losing focus.
    private val composingReading = StringBuilder()
    private var candidateJob: Job? = null
    private lateinit var serviceScope: CoroutineScope
    private val repository by lazy { DictionaryRepository(DictionaryDatabase.getInstance(applicationContext)) }

    override fun onCreate() {
        super.onCreate()
        layout = assets.open(LAYOUT_ASSET).use { it.reader().readText() }.let(LayoutJson::decode)
        serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        candidateTypeface = FontImporter.currentFont(this)?.let { runCatching { Typeface.createFromFile(it) }.getOrNull() }

        gridView = KeyboardGridView(this).apply {
            imageProvider = AssetKeyImageProvider()
            onKeyTapped = ::handleKeyTapped
        }
        renderCurrentPage()

        val strip = LinearLayout(this).also { candidateContainer = it }
        val candidateStripHeight = resources.getDimensionPixelSize(R.dimen.candidate_strip_height)
        val candidateScroller = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(strip)
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(candidateScroller, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, candidateStripHeight))
            addView(gridView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        }

        val totalHeight = resources.getDimensionPixelSize(R.dimen.keyboard_height) + candidateStripHeight
        return FixedHeightContainer(this, totalHeight).apply {
            setContent(content)
        }
    }

    // A custom IME view has no inherent size hint for the framework, so without this it can be
    // stretched to fill the whole screen instead of a normal keyboard-sized strip at the bottom.
    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onFinishInput() {
        currentInputConnection?.finishComposingText()
        clearComposing()
        super.onFinishInput()
    }

    private fun renderCurrentPage() {
        gridView.setPage(layout.pages[pageIndex])
    }

    private fun handleKeyTapped(key: KeyDef) {
        val ic = currentInputConnection ?: return
        when (key.role) {
            KeyRole.CHAR -> {
                composingReading.append(key.text.orEmpty())
                ic.setComposingText(composingReading, 1)
                updateCandidates(ic)
            }
            KeyRole.ENTER -> {
                if (composingReading.isNotEmpty()) {
                    ic.commitText(composingReading, 1)
                    clearComposing()
                } else {
                    ic.commitText("\n", 1)
                }
            }
            KeyRole.DELETE -> {
                if (composingReading.isNotEmpty()) {
                    composingReading.deleteCharAt(composingReading.length - 1)
                    if (composingReading.isEmpty()) {
                        ic.setComposingText("", 1)
                        clearCandidates()
                    } else {
                        ic.setComposingText(composingReading, 1)
                        updateCandidates(ic)
                    }
                } else {
                    ic.deleteSurroundingText(1, 0)
                }
            }
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

    private fun updateCandidates(ic: InputConnection) {
        candidateJob?.cancel()
        val prefix = composingReading.toString()
        candidateJob = serviceScope.launch {
            val candidates = repository.candidatesForPrefix(prefix, MAX_CANDIDATES)
            renderCandidates(candidates, ic)
        }
    }

    private fun renderCandidates(candidates: List<WordEntity>, ic: InputConnection) {
        val strip = candidateContainer ?: return
        strip.removeAllViews()
        val hPadding = (16 * resources.displayMetrics.density).toInt()
        for (word in candidates) {
            val chip = TextView(this).apply {
                text = word.target
                typeface = candidateTypeface
                setPadding(hPadding, 0, hPadding, 0)
                setOnClickListener {
                    ic.commitText(word.target, 1)
                    clearComposing()
                }
            }
            strip.addView(chip)
        }
    }

    private fun clearComposing() {
        candidateJob?.cancel()
        composingReading.clear()
        clearCandidates()
    }

    private fun clearCandidates() {
        candidateContainer?.removeAllViews()
    }

    private inner class AssetKeyImageProvider : KeyImageProvider {
        override fun getBitmap(imagePath: String): Bitmap? = runCatching {
            assets.open("$IMAGE_ASSET_DIR/$imagePath").use { android.graphics.BitmapFactory.decodeStream(it) }
        }.getOrNull()
    }
}
