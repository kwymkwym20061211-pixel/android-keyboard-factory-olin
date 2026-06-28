package android.keyboard.template.dictionary.ui

import android.app.Application
import android.keyboard.template.dictionary.data.DictionaryDatabase
import android.keyboard.template.dictionary.data.DictionaryEntity
import android.keyboard.template.dictionary.data.DictionaryRepository
import android.keyboard.template.dictionary.data.WordEntity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DictionaryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DictionaryRepository(DictionaryDatabase.getInstance(application))

    val dictionaries: StateFlow<List<DictionaryEntity>> =
        repository.observeDictionaries().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val selectedId = MutableStateFlow<Long?>(null)
    val selectedDictionaryId: StateFlow<Long?> = selectedId

    @OptIn(ExperimentalCoroutinesApi::class)
    val words: StateFlow<List<WordEntity>> = selectedId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repository.observeWords(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        // Keep the selection valid as the dictionary list changes (e.g. fall back to the first
        // dictionary after the previously-selected one is deleted, or once the first one loads).
        viewModelScope.launch {
            dictionaries.collect { list ->
                if (selectedId.value == null || list.none { it.id == selectedId.value }) {
                    selectedId.value = list.firstOrNull()?.id
                }
            }
        }
    }

    fun selectDictionary(id: Long) {
        selectedId.value = id
    }

    fun createDictionary(name: String, onResult: (Result<Long>) -> Unit) {
        viewModelScope.launch {
            val result = repository.createDictionary(name)
            result.onSuccess { selectedId.value = it }
            onResult(result)
        }
    }

    fun deleteDictionary(dictionary: DictionaryEntity) {
        viewModelScope.launch { repository.deleteDictionary(dictionary) }
    }

    fun addWord(reading: String, target: String, onResult: (Result<Long>) -> Unit) {
        val dictionaryId = selectedId.value ?: return
        viewModelScope.launch { onResult(repository.addWord(dictionaryId, reading, target)) }
    }

    fun updateWord(word: WordEntity) {
        viewModelScope.launch { repository.updateWord(word) }
    }

    fun deleteWord(word: WordEntity) {
        viewModelScope.launch { repository.deleteWord(word) }
    }

    fun importCsv(fileName: String, content: String, onResult: (Result<Long>) -> Unit) {
        viewModelScope.launch {
            val result = repository.importCsv(fileName, content)
            result.onSuccess { selectedId.value = it }
            onResult(result)
        }
    }

    /** Dictionary name + its CSV text for the currently-selected dictionary, or null if none is
     * selected. Writing the result to storage is left to the caller (Activity), consistent with
     * the rest of this app's Android-IO-stays-in-the-Activity convention. */
    suspend fun exportCsvForSelected(): Pair<String, String>? {
        val dictionary = dictionaries.value.firstOrNull { it.id == selectedId.value } ?: return null
        return dictionary.name to repository.exportCsv(dictionary.id)
    }
}
