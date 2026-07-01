package android.keyboard.factory.olin.ui

import android.app.Application
import android.keyboard.engine.CellState
import android.keyboard.engine.Direction
import android.keyboard.engine.KeyRole
import android.keyboard.engine.ShapeNormalizer
import android.keyboard.factory.olin.data.KeyCellEntity
import android.keyboard.factory.olin.data.KeyboardFactoryDatabase
import android.keyboard.factory.olin.data.KeyboardProjectEntity
import android.keyboard.factory.olin.data.KeyboardProjectRepository
import android.keyboard.factory.olin.data.PageEntity
import android.keyboard.factory.olin.export.KeyboardExportPipeline
import android.keyboard.factory.olin.exportimport.KeyboardProjectZipIo
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EditorViewModel(application: Application, private val projectId: Long) : AndroidViewModel(application) {

    private val db = KeyboardFactoryDatabase.getInstance(application)
    private val repository = KeyboardProjectRepository(db)

    private val currentPageIndex = MutableStateFlow(0)

    val project: StateFlow<KeyboardProjectEntity?> = repository.observeProject(projectId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val pages: StateFlow<List<PageEntity>> = repository.pagesOf(projectId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val pageIndex: StateFlow<Int> = currentPageIndex

    val currentPage: StateFlow<PageEntity?> = combine(pages, currentPageIndex) { pages, index ->
        pages.getOrNull(index)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val cells: StateFlow<List<KeyCellEntity>> = currentPage
        .flatMapLatest { page -> page?.let { repository.cellsOf(it.id) } ?: flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun goToPrevPage() {
        if (currentPageIndex.value > 0) currentPageIndex.value -= 1
    }

    fun goToNextPage() {
        if (currentPageIndex.value < pages.value.size - 1) currentPageIndex.value += 1
    }

    fun addPage(rows: Int, cols: Int) {
        viewModelScope.launch {
            val newIndex = pages.value.size
            repository.addPage(projectId, rows, cols)
            currentPageIndex.value = newIndex
        }
    }

    fun deleteCurrentPage(onResult: (Boolean) -> Unit) {
        val page = currentPage.value ?: run { onResult(false); return }
        val totalPages = pages.value.size
        viewModelScope.launch {
            val deleted = repository.deletePage(page.id)
            if (deleted) {
                currentPageIndex.value = currentPageIndex.value.coerceAtMost(totalPages - 2).coerceAtLeast(0)
            }
            onResult(deleted)
        }
    }

    fun updateRole(cellId: Long, role: KeyRole, text: String?) {
        viewModelScope.launch {
            val cell = cells.value.firstOrNull { it.id == cellId } ?: return@launch
            db.keyCellDao().update(cell.copy(role = role, text = if (role == KeyRole.CHAR) text else null))
        }
    }

    fun mergeDirection(cellId: Long, direction: Direction) = mutateCells { ShapeNormalizer.mergeInDirection(it, cellId, direction) }

    fun unmerge(cellId: Long) = mutateCells { ShapeNormalizer.unmerge(it, cellId) }

    private fun mutateCells(transform: (List<CellState>) -> List<CellState>) {
        val page = currentPage.value ?: return
        viewModelScope.launch {
            val states = cells.value.map { it.toCellState() }
            val updated = transform(states).map { it.toEntity(page.id) }
            db.keyCellDao().updateAll(updated)
        }
    }

    fun renameProject(name: String) {
        viewModelScope.launch { repository.renameProject(projectId, name) }
    }

    fun setDefaultFontPath(path: String?) {
        viewModelScope.launch { repository.setDefaultFontPath(projectId, path) }
    }

    fun setIconPath(path: String?) {
        viewModelScope.launch { repository.setIconPath(projectId, path) }
    }

    fun resizeCurrentPage(rows: Int, cols: Int) {
        val page = currentPage.value ?: return
        viewModelScope.launch { repository.resizePage(page.id, rows, cols) }
    }

    fun setCurrentPageFontOverride(path: String?) {
        val page = currentPage.value ?: return
        viewModelScope.launch { repository.setPageFontOverride(page.id, path) }
    }

    fun export(onResult: (Result<KeyboardExportPipeline.Result>) -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { KeyboardExportPipeline.export(getApplication(), projectId) }
            }
            onResult(result)
        }
    }

    fun exportProject(onResult: (Result<KeyboardProjectZipIo.ExportResult>) -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { KeyboardProjectZipIo.export(getApplication(), projectId) }
            }
            onResult(result)
        }
    }

    class Factory(private val application: Application, private val projectId: Long) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = EditorViewModel(application, projectId) as T
    }
}
