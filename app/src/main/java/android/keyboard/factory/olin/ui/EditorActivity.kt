package android.keyboard.factory.olin.ui

import android.keyboard.engine.Direction
import android.keyboard.engine.KeyDef
import android.keyboard.engine.KeyRole
import android.keyboard.factory.olin.R
import android.keyboard.factory.olin.databinding.ActivityEditorBinding
import android.keyboard.factory.olin.databinding.DialogAddPageBinding
import android.keyboard.factory.olin.databinding.DialogEditKeyBinding
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class EditorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditorBinding
    private val projectId: Long by lazy { intent.getLongExtra(EXTRA_PROJECT_ID, -1L) }
    private val viewModel: EditorViewModel by viewModels { EditorViewModel.Factory(application, projectId) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.prevPageButton.setOnClickListener { viewModel.goToPrevPage() }
        binding.nextPageButton.setOnClickListener { viewModel.goToNextPage() }
        binding.addPageButton.setOnClickListener { showAddPageDialog() }
        binding.gridView.onKeyTapped = { key -> showEditKeyDialog(key) }

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

    private fun showEditKeyDialog(key: KeyDef) {
        val owner = ownerOf(key, viewModel.cells.value)
        val dialogBinding = DialogEditKeyBinding.inflate(layoutInflater)

        val roleToButtonId = mapOf(
            KeyRole.CHAR to dialogBinding.roleChar.id,
            KeyRole.ENTER to dialogBinding.roleEnter.id,
            KeyRole.DELETE to dialogBinding.roleDelete.id,
            KeyRole.PAGE_NEXT to dialogBinding.rolePageNext.id,
            KeyRole.PAGE_PREV to dialogBinding.rolePagePrev.id,
            KeyRole.NONE to dialogBinding.roleNone.id,
        )
        dialogBinding.roleGroup.check(roleToButtonId.getValue(owner.role))
        dialogBinding.charTextInput.setText(owner.text.orEmpty())
        dialogBinding.charTextInput.isEnabled = owner.role == KeyRole.CHAR
        dialogBinding.roleGroup.setOnCheckedChangeListener { _, checkedId ->
            dialogBinding.charTextInput.isEnabled = checkedId == dialogBinding.roleChar.id
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.save) { _, _ ->
                val role = roleToButtonId.entries.first { it.value == dialogBinding.roleGroup.checkedRadioButtonId }.key
                viewModel.updateRole(owner.id, role, dialogBinding.charTextInput.text?.toString())
            }
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialogBinding.mergeUpButton.setOnClickListener { viewModel.mergeDirection(owner.id, Direction.UP); dialog.dismiss() }
        dialogBinding.mergeDownButton.setOnClickListener { viewModel.mergeDirection(owner.id, Direction.DOWN); dialog.dismiss() }
        dialogBinding.mergeLeftButton.setOnClickListener { viewModel.mergeDirection(owner.id, Direction.LEFT); dialog.dismiss() }
        dialogBinding.mergeRightButton.setOnClickListener { viewModel.mergeDirection(owner.id, Direction.RIGHT); dialog.dismiss() }
        dialogBinding.splitOutButton.setOnClickListener { viewModel.unmerge(owner.id); dialog.dismiss() }

        dialog.show()
    }

    companion object {
        const val EXTRA_PROJECT_ID = "project_id"
    }
}
