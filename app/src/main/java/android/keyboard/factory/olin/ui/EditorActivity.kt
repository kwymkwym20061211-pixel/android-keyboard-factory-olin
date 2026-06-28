package android.keyboard.factory.olin.ui

import android.content.Intent
import android.graphics.Typeface
import android.keyboard.engine.Direction
import android.keyboard.engine.KeyDef
import android.keyboard.engine.KeyRole
import android.keyboard.factory.olin.R
import android.keyboard.factory.olin.databinding.ActivityEditorBinding
import android.keyboard.factory.olin.databinding.DialogAddPageBinding
import android.keyboard.factory.olin.databinding.DialogEditKeyBinding
import android.keyboard.factory.olin.databinding.DialogPageSettingsBinding
import android.keyboard.factory.olin.databinding.DialogProjectSettingsBinding
import android.keyboard.factory.olin.databinding.DialogUnicodeInputBinding
import android.keyboard.factory.olin.font.FontImporter
import android.keyboard.factory.olin.icon.IconImporter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import java.io.File
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class EditorActivity : AppCompatActivity() {

    private enum class FontTarget { PROJECT, PAGE }

    private lateinit var binding: ActivityEditorBinding
    private val projectId: Long by lazy { intent.getLongExtra(EXTRA_PROJECT_ID, -1L) }
    private val viewModel: EditorViewModel by viewModels { EditorViewModel.Factory(application, projectId) }

    private var pendingFontTarget: FontTarget? = null
    private var activeFontPathLabel: TextView? = null
    private var activeIconPreview: ImageView? = null
    private var activeIconPathLabel: TextView? = null
    private var lastExportUri: Uri? = null

    private val pickFontLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        val target = pendingFontTarget ?: return@registerForActivityResult
        val fileName = when (target) {
            FontTarget.PROJECT -> "project_${projectId}_default.ttf"
            FontTarget.PAGE -> "page_${viewModel.currentPage.value?.id}_override.ttf"
        }
        val file = FontImporter.import(this, uri, fileName)
        when (target) {
            FontTarget.PROJECT -> viewModel.setDefaultFontPath(file.absolutePath)
            FontTarget.PAGE -> viewModel.setCurrentPageFontOverride(file.absolutePath)
        }
        activeFontPathLabel?.text = getString(R.string.font_path_set_format, file.name)
    }

    private val pickIconLauncher = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@registerForActivityResult
        val file = IconImporter.import(this, uri, "project_${projectId}_icon.png")
        viewModel.setIconPath(file.absolutePath)
        setIconPreview(activeIconPreview, activeIconPathLabel, file.absolutePath)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        binding.prevPageButton.setOnClickListener { viewModel.goToPrevPage() }
        binding.nextPageButton.setOnClickListener { viewModel.goToNextPage() }
        binding.addPageButton.setOnClickListener { showAddPageDialog() }
        binding.pageSettingsButton.setOnClickListener { showPageSettingsDialog() }
        binding.gridView.onKeyTapped = { key -> showEditKeyDialog(key) }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.project.collect { project ->
                    supportActionBar?.title = project?.name ?: getString(R.string.app_name)
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(viewModel.pages, viewModel.pageIndex, viewModel.cells) { pages, index, cells ->
                    Triple(pages, index, cells)
                }.collect { (pages, index, cells) ->
                    val page = pages.getOrNull(index)
                    binding.prevPageButton.isEnabled = index > 0
                    binding.nextPageButton.isEnabled = index < pages.size - 1
                    if (page != null) {
                        binding.pageLabel.text = getString(R.string.page_label_format, index + 1, pages.size, page.rows, page.cols)
                        binding.gridView.setPage(toPageLayout(page, cells))
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(viewModel.currentPage, viewModel.project) { page, project ->
                    page?.fontPathOverride ?: project?.defaultFontPath
                }.collect { fontPath -> binding.gridView.typeface = resolveTypeface(fontPath) }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.editor_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_project_settings -> {
            showProjectSettingsDialog()
            true
        }
        R.id.action_export -> {
            runExport()
            true
        }
        R.id.action_export_project -> {
            runExportProject()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun showAddPageDialog() {
        val dialogBinding = DialogAddPageBinding.inflate(layoutInflater)
        AlertDialog.Builder(this)
            .setTitle(R.string.page_add)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.create) { _, _ ->
                val rows = dialogBinding.rowsInput.text?.toString()?.toIntOrNull() ?: 4
                val cols = dialogBinding.colsInput.text?.toString()?.toIntOrNull() ?: 10
                viewModel.addPage(rows.coerceIn(1, 32), cols.coerceIn(1, 32))
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showProjectSettingsDialog() {
        val project = viewModel.project.value ?: return
        val dialogBinding = DialogProjectSettingsBinding.inflate(layoutInflater)
        dialogBinding.nameInput.setText(project.name)
        setFontLabel(dialogBinding.fontPathLabel, project.defaultFontPath)

        dialogBinding.pickFontButton.setOnClickListener {
            pendingFontTarget = FontTarget.PROJECT
            activeFontPathLabel = dialogBinding.fontPathLabel
            pickFontLauncher.launch(arrayOf("*/*"))
        }
        dialogBinding.clearFontButton.setOnClickListener {
            viewModel.setDefaultFontPath(null)
            setFontLabel(dialogBinding.fontPathLabel, null)
        }

        activeIconPreview = dialogBinding.iconPreview
        activeIconPathLabel = dialogBinding.iconPathLabel
        setIconPreview(dialogBinding.iconPreview, dialogBinding.iconPathLabel, project.iconPath)

        dialogBinding.pickIconButton.setOnClickListener {
            pickIconLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        dialogBinding.clearIconButton.setOnClickListener {
            viewModel.setIconPath(null)
            setIconPreview(dialogBinding.iconPreview, dialogBinding.iconPathLabel, null)
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.project_settings_title)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = dialogBinding.nameInput.text?.toString()?.trim().orEmpty()
                if (name.isNotEmpty()) viewModel.renameProject(name)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showPageSettingsDialog() {
        val page = viewModel.currentPage.value ?: return
        val dialogBinding = DialogPageSettingsBinding.inflate(layoutInflater)
        dialogBinding.rowsInput.setText(page.rows.toString())
        dialogBinding.colsInput.setText(page.cols.toString())
        setFontLabel(dialogBinding.fontPathLabel, page.fontPathOverride)

        dialogBinding.pickFontButton.setOnClickListener {
            pendingFontTarget = FontTarget.PAGE
            activeFontPathLabel = dialogBinding.fontPathLabel
            pickFontLauncher.launch(arrayOf("*/*"))
        }
        dialogBinding.clearFontButton.setOnClickListener {
            viewModel.setCurrentPageFontOverride(null)
            setFontLabel(dialogBinding.fontPathLabel, null)
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.page_settings_title)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.save) { _, _ ->
                val rows = dialogBinding.rowsInput.text?.toString()?.toIntOrNull() ?: page.rows
                val cols = dialogBinding.colsInput.text?.toString()?.toIntOrNull() ?: page.cols
                viewModel.resizeCurrentPage(rows.coerceIn(1, 32), cols.coerceIn(1, 32))
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun setFontLabel(label: TextView, path: String?) {
        label.text = if (path == null) getString(R.string.font_path_none) else getString(R.string.font_path_set_format, File(path).name)
    }

    private fun setIconPreview(preview: ImageView?, label: TextView?, path: String?) {
        if (path == null) {
            preview?.setImageDrawable(null)
            label?.text = getString(R.string.icon_not_set)
        } else {
            preview?.setImageURI(Uri.fromFile(File(path)))
            label?.text = ""
        }
    }

    private fun resolveTypeface(fontPath: String?): Typeface =
        fontPath
            ?.let { File(it) }
            ?.takeIf { it.exists() }
            ?.let { runCatching { Typeface.createFromFile(it) }.getOrNull() }
            ?: Typeface.DEFAULT

    private fun currentPageTypeface(): Typeface =
        resolveTypeface(viewModel.currentPage.value?.fontPathOverride ?: viewModel.project.value?.defaultFontPath)

    private fun showEditKeyDialog(key: KeyDef) {
        val owner = ownerOf(key, viewModel.cells.value)
        val dialogBinding = DialogEditKeyBinding.inflate(layoutInflater)
        val pageTypeface = currentPageTypeface()

        val roleToButtonId = mapOf(
            KeyRole.CHAR to dialogBinding.roleChar.id,
            KeyRole.ENTER to dialogBinding.roleEnter.id,
            KeyRole.DELETE to dialogBinding.roleDelete.id,
            KeyRole.PAGE_NEXT to dialogBinding.rolePageNext.id,
            KeyRole.PAGE_PREV to dialogBinding.rolePagePrev.id,
            KeyRole.NONE to dialogBinding.roleNone.id,
        )
        dialogBinding.roleGroup.check(roleToButtonId.getValue(owner.role))
        dialogBinding.charTextInput.typeface = pageTypeface
        dialogBinding.charTextInput.setText(owner.text.orEmpty())
        dialogBinding.charTextInput.isEnabled = owner.role == KeyRole.CHAR
        dialogBinding.unicodeInputButton.isEnabled = owner.role == KeyRole.CHAR
        dialogBinding.roleGroup.setOnCheckedChangeListener { _, checkedId ->
            dialogBinding.charTextInput.isEnabled = checkedId == dialogBinding.roleChar.id
            dialogBinding.unicodeInputButton.isEnabled = checkedId == dialogBinding.roleChar.id
        }
        dialogBinding.unicodeInputButton.setOnClickListener {
            showUnicodeInputDialog(pageTypeface) { char -> dialogBinding.charTextInput.append(char) }
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.save) { _, _ ->
                val role = roleToButtonId.entries.first { it.value == dialogBinding.roleGroup.checkedRadioButtonId }.key
                viewModel.updateRole(owner.id, role, dialogBinding.charTextInput.text?.toString())
            }
            .setNegativeButton(R.string.cancel, null)
            .create()

        // A cell already merged into a group of 2+ has no unambiguous "merge target" (which member's
        // position should anchor the new direction?), so it can only release the whole group at once.
        val isGrouped = viewModel.cells.value.count { (it.ownerCellId ?: it.id) == owner.id } > 1
        dialogBinding.mergeSection.visibility = if (isGrouped) View.GONE else View.VISIBLE
        dialogBinding.unmergeSection.visibility = if (isGrouped) View.VISIBLE else View.GONE

        dialogBinding.mergeUpButton.setOnClickListener { viewModel.mergeDirection(owner.id, Direction.UP); dialog.dismiss() }
        dialogBinding.mergeDownButton.setOnClickListener { viewModel.mergeDirection(owner.id, Direction.DOWN); dialog.dismiss() }
        dialogBinding.mergeLeftButton.setOnClickListener { viewModel.mergeDirection(owner.id, Direction.LEFT); dialog.dismiss() }
        dialogBinding.mergeRightButton.setOnClickListener { viewModel.mergeDirection(owner.id, Direction.RIGHT); dialog.dismiss() }
        dialogBinding.unmergeAllButton.setOnClickListener { viewModel.unmerge(owner.id); dialog.dismiss() }

        dialog.show()
    }

    private fun showUnicodeInputDialog(typeface: Typeface, onConfirm: (String) -> Unit) {
        val dialogBinding = DialogUnicodeInputBinding.inflate(layoutInflater)
        dialogBinding.unicodePreview.typeface = typeface

        var codePoint = 0
        fun refresh() {
            val isValid = Character.isValidCodePoint(codePoint)
            dialogBinding.unicodeHexDisplay.text = getString(R.string.unicode_hex_format, "%06X".format(codePoint))
            dialogBinding.unicodePreview.text = if (isValid) String(Character.toChars(codePoint)) else ""
            dialogBinding.unicodeEnterButton.isEnabled = isValid
        }
        refresh()

        val digitButtons = listOf(
            dialogBinding.hexButton0 to 0x0, dialogBinding.hexButton1 to 0x1,
            dialogBinding.hexButton2 to 0x2, dialogBinding.hexButton3 to 0x3,
            dialogBinding.hexButton4 to 0x4, dialogBinding.hexButton5 to 0x5,
            dialogBinding.hexButton6 to 0x6, dialogBinding.hexButton7 to 0x7,
            dialogBinding.hexButton8 to 0x8, dialogBinding.hexButton9 to 0x9,
            dialogBinding.hexButtonA to 0xA, dialogBinding.hexButtonB to 0xB,
            dialogBinding.hexButtonC to 0xC, dialogBinding.hexButtonD to 0xD,
            dialogBinding.hexButtonE to 0xE, dialogBinding.hexButtonF to 0xF,
        )
        digitButtons.forEach { (button, digit) ->
            button.setOnClickListener {
                codePoint = ((codePoint shl 4) or digit) and CODE_POINT_HEX_MASK
                refresh()
            }
        }
        dialogBinding.unicodeDeleteButton.setOnClickListener {
            codePoint = codePoint ushr 4
            refresh()
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()
        dialogBinding.unicodeEnterButton.setOnClickListener {
            onConfirm(String(Character.toChars(codePoint)))
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun runExport() {
        Toast.makeText(this, R.string.export_in_progress, Toast.LENGTH_SHORT).show()
        viewModel.export { result ->
            result.onSuccess { exportResult ->
                lastExportUri = exportResult.downloadsUri
                AlertDialog.Builder(this)
                    .setMessage(getString(R.string.export_success_format, exportResult.displayName))
                    .setPositiveButton(R.string.install) { _, _ -> promptInstall(exportResult.downloadsUri) }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }.onFailure { error ->
                Toast.makeText(this, getString(R.string.export_failure_format, error.message), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun runExportProject() {
        Toast.makeText(this, R.string.export_in_progress, Toast.LENGTH_SHORT).show()
        viewModel.exportProject { result ->
            result.onSuccess { exportResult ->
                Toast.makeText(this, getString(R.string.export_success_format, exportResult.displayName), Toast.LENGTH_LONG).show()
            }.onFailure { error ->
                Toast.makeText(this, getString(R.string.export_failure_format, error.message), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun promptInstall(uri: Uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !packageManager.canRequestPackageInstalls()) {
            startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
            return
        }
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(installIntent)
    }

    companion object {
        const val EXTRA_PROJECT_ID = "project_id"

        // 6 hex digits (24 bits) covers every valid Unicode code point (max U+10FFFF).
        private const val CODE_POINT_HEX_MASK = 0xFFFFFF
    }
}
