package android.keyboard.factory.olin.ui

import android.content.Intent
import android.keyboard.factory.olin.R
import android.keyboard.factory.olin.databinding.ActivityProjectListBinding
import android.keyboard.factory.olin.databinding.DialogCreateProjectBinding
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch

class ProjectListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProjectListBinding
    private val viewModel: ProjectListViewModel by viewModels()
    private lateinit var adapter: ProjectListAdapter

    private val pickKeyboardFileLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importProject(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProjectListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        adapter = ProjectListAdapter(
            onItemClick = { project -> openEditor(project.id) },
            onDeleteClick = { project -> viewModel.deleteProject(project) },
        )
        binding.projectList.layoutManager = LinearLayoutManager(this)
        binding.projectList.adapter = adapter

        binding.searchInput.addTextChangedListener { text -> viewModel.setQuery(text?.toString().orEmpty()) }
        binding.newProjectButton.setOnClickListener { showCreateProjectDialog() }
        binding.importProjectButton.setOnClickListener { pickKeyboardFileLauncher.launch(arrayOf("*/*")) }

        lifecycleScope.launch {
            viewModel.projects.collect { adapter.submitList(it) }
        }

        handleViewIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleViewIntent(intent)
    }

    // Lets a file manager's "open with" for a tapped .keyboard file (see the manifest
    // intent-filter on this activity) reuse the same import path as the in-app button.
    private fun handleViewIntent(intent: Intent) {
        if (intent.action != Intent.ACTION_VIEW) return
        val uri = intent.data ?: return
        importProject(uri)
    }

    private fun importProject(uri: Uri) {
        viewModel.importProject(uri) { result ->
            result.onSuccess { projectId -> openEditor(projectId) }
                .onFailure { error ->
                    Toast.makeText(this, getString(R.string.import_project_failure_format, error.message), Toast.LENGTH_LONG).show()
                }
        }
    }

    private fun openEditor(projectId: Long) {
        startActivity(Intent(this, EditorActivity::class.java).putExtra(EditorActivity.EXTRA_PROJECT_ID, projectId))
    }

    private fun showCreateProjectDialog() {
        val dialogBinding = DialogCreateProjectBinding.inflate(layoutInflater)
        AlertDialog.Builder(this)
            .setTitle(R.string.new_project_title)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.create) { _, _ ->
                val name = dialogBinding.nameInput.text?.toString()?.trim().orEmpty()
                if (name.isEmpty()) return@setPositiveButton
                val rows = dialogBinding.rowsInput.text?.toString()?.toIntOrNull() ?: DEFAULT_ROWS
                val cols = dialogBinding.colsInput.text?.toString()?.toIntOrNull() ?: DEFAULT_COLS
                viewModel.createProject(name, rows.coerceIn(1, 32), cols.coerceIn(1, 32)) { id -> openEditor(id) }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    companion object {
        private const val DEFAULT_ROWS = 4
        private const val DEFAULT_COLS = 10
    }
}
