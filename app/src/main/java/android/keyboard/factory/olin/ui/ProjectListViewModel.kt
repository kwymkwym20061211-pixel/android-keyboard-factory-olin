package android.keyboard.factory.olin.ui

import android.app.Application
import android.keyboard.factory.olin.data.KeyboardFactoryDatabase
import android.keyboard.factory.olin.data.KeyboardProjectEntity
import android.keyboard.factory.olin.data.KeyboardProjectRepository
import android.keyboard.factory.olin.exportimport.KeyboardProjectZipIo
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProjectListViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = KeyboardProjectRepository(KeyboardFactoryDatabase.getInstance(application))

    private val query = MutableStateFlow("")

    @OptIn(ExperimentalCoroutinesApi::class)
    val projects: StateFlow<List<KeyboardProjectEntity>> = query
        .flatMapLatest { repository.search(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(text: String) {
        query.value = text
    }

    fun createProject(name: String, rows: Int, cols: Int, onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val id = repository.createProject(name, rows, cols)
            onCreated(id)
        }
    }

    fun deleteProject(project: KeyboardProjectEntity) {
        viewModelScope.launch { repository.deleteProject(project) }
    }

    fun importProject(uri: Uri, onResult: (Result<Long>) -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { KeyboardProjectZipIo.import(getApplication(), uri) }
            }
            onResult(result)
        }
    }
}
