package android.keyboard.template.dictionary.ui

import android.keyboard.template.CacheCleanup
import android.keyboard.template.R
import android.keyboard.template.databinding.ActivityDictionaryBinding
import android.keyboard.template.databinding.DialogCreateDictionaryBinding
import android.keyboard.template.databinding.DialogEditWordBinding
import android.keyboard.template.dictionary.csv.DictionaryCsv
import android.keyboard.template.dictionary.csv.DictionaryCsvIo
import android.keyboard.template.dictionary.data.DictionaryEntity
import android.keyboard.template.dictionary.data.DictionaryError
import android.keyboard.template.dictionary.data.WordEntity
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DictionaryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDictionaryBinding
    private val viewModel: DictionaryViewModel by viewModels()
    private lateinit var adapter: WordAdapter

    private var dictionaries: List<DictionaryEntity> = emptyList()
    private var suppressSpinnerCallback = false

    private val importCsvLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            val fileName = DictionaryCsvIo.displayName(this@DictionaryActivity, uri)
            if (fileName == null) {
                showError(getString(R.string.import_error_title), getString(R.string.import_filename_unavailable))
                return@launch
            }
            val content = withContext(Dispatchers.IO) { DictionaryCsvIo.readText(this@DictionaryActivity, uri) }
            viewModel.importCsv(fileName, content) { result ->
                result.onSuccess {
                    Toast.makeText(this@DictionaryActivity, getString(R.string.import_success_format, fileName), Toast.LENGTH_SHORT).show()
                }.onFailure { error ->
                    showError(getString(R.string.import_error_title), errorMessage(error))
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CacheCleanup.clearOnce(this)
        binding = ActivityDictionaryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        adapter = WordAdapter(
            onItemClick = { word -> showEditWordDialog(word) },
            onDeleteClick = { word -> viewModel.deleteWord(word) },
        )
        binding.wordList.layoutManager = LinearLayoutManager(this)
        binding.wordList.adapter = adapter

        binding.dictionarySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (suppressSpinnerCallback) return
                dictionaries.getOrNull(position)?.let { viewModel.selectDictionary(it.id) }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        binding.createDictionaryButton.setOnClickListener { showCreateDictionaryDialog() }
        binding.overflowMenuButton.setOnClickListener { showOverflowMenu(it) }
        binding.addWordButton.setOnClickListener { showAddWordDialog() }

        lifecycleScope.launch {
            viewModel.dictionaries.collect { list -> onDictionariesChanged(list) }
        }
        lifecycleScope.launch {
            viewModel.words.collect { adapter.submitList(it) }
        }
    }

    private fun onDictionariesChanged(list: List<DictionaryEntity>) {
        dictionaries = list
        binding.dictionarySpinner.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, list.map { it.name })

        val selectedIndex = list.indexOfFirst { it.id == viewModel.selectedDictionaryId.value }
        if (selectedIndex >= 0) {
            suppressSpinnerCallback = true
            binding.dictionarySpinner.setSelection(selectedIndex)
            suppressSpinnerCallback = false
        }

        val empty = list.isEmpty()
        binding.emptyStateText.visibility = if (empty) View.VISIBLE else View.GONE
        binding.wordList.visibility = if (empty) View.GONE else View.VISIBLE
        binding.addWordButton.visibility = if (empty) View.GONE else View.VISIBLE
        binding.overflowMenuButton.isEnabled = !empty
    }

    private fun showOverflowMenu(anchor: View) {
        val dictionary = dictionaries.firstOrNull { it.id == viewModel.selectedDictionaryId.value } ?: return
        PopupMenu(this, anchor).apply {
            menuInflater.inflate(R.menu.dictionary_overflow_menu, menu)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_export -> { runExport(); true }
                    R.id.action_import -> { importCsvLauncher.launch(arrayOf("*/*")); true }
                    R.id.action_delete_dictionary -> { confirmDeleteDictionary(dictionary); true }
                    else -> false
                }
            }
            show()
        }
    }

    private fun runExport() {
        lifecycleScope.launch {
            val (name, csv) = viewModel.exportCsvForSelected() ?: return@launch
            val fileName = "$name.csv"
            withContext(Dispatchers.IO) { DictionaryCsvIo.writeToDownloads(this@DictionaryActivity, fileName, csv) }
            Toast.makeText(this@DictionaryActivity, getString(R.string.export_success_format, fileName), Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmDeleteDictionary(dictionary: DictionaryEntity) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_dictionary_confirm_title)
            .setMessage(getString(R.string.delete_dictionary_confirm_message, dictionary.name))
            .setPositiveButton(R.string.delete) { _, _ -> viewModel.deleteDictionary(dictionary) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showCreateDictionaryDialog() {
        val dialogBinding = DialogCreateDictionaryBinding.inflate(layoutInflater)
        AlertDialog.Builder(this)
            .setTitle(R.string.create_dictionary_title)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.create) { _, _ ->
                val name = dialogBinding.nameInput.text?.toString().orEmpty()
                viewModel.createDictionary(name) { result ->
                    result.onFailure { error -> showError(getString(R.string.create_dictionary_error_title), errorMessage(error)) }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showAddWordDialog() {
        val dialogBinding = DialogEditWordBinding.inflate(layoutInflater)
        AlertDialog.Builder(this)
            .setTitle(R.string.add_word_title)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.save) { _, _ ->
                val reading = dialogBinding.readingInput.text?.toString().orEmpty()
                val target = dialogBinding.targetInput.text?.toString().orEmpty()
                viewModel.addWord(reading, target) { result ->
                    result.onFailure { error -> showError(getString(R.string.add_word_title), errorMessage(error)) }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showEditWordDialog(word: WordEntity) {
        val dialogBinding = DialogEditWordBinding.inflate(layoutInflater)
        dialogBinding.readingInput.setText(word.reading)
        dialogBinding.targetInput.setText(word.target)
        AlertDialog.Builder(this)
            .setTitle(R.string.edit_word_title)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.save) { _, _ ->
                val reading = dialogBinding.readingInput.text?.toString().orEmpty()
                val target = dialogBinding.targetInput.text?.toString().orEmpty()
                if (reading.isNotBlank() && target.isNotBlank()) {
                    viewModel.updateWord(word.copy(reading = reading, target = target))
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showError(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    private fun errorMessage(error: Throwable): String = when (error) {
        is DictionaryError.EmptyName -> getString(R.string.error_dictionary_name_required)
        is DictionaryError.InvalidNameChar -> getString(R.string.error_dictionary_name_invalid_char, error.char.toString())
        is DictionaryError.DuplicateName -> getString(R.string.error_dictionary_name_duplicate, error.name)
        is DictionaryError.EmptyWordFields -> getString(R.string.error_word_fields_required)
        is DictionaryError.InvalidCsvFileName -> getString(R.string.error_csv_filename_must_end_with_csv)
        is DictionaryError.CsvParseError ->
            getString(R.string.error_csv_parse_line_prefix, error.lineNumber, csvErrorReasonMessage(error.reason))
        else -> error.message.orEmpty()
    }

    private fun csvErrorReasonMessage(reason: DictionaryCsv.CsvErrorReason): String = when (reason) {
        is DictionaryCsv.CsvErrorReason.TrailingBackslash -> getString(R.string.error_csv_trailing_backslash)
        is DictionaryCsv.CsvErrorReason.WrongColumnCount -> getString(R.string.error_csv_wrong_column_count, reason.count)
        is DictionaryCsv.CsvErrorReason.EmptyField -> getString(R.string.error_csv_empty_field)
    }
}
