package android.keyboard.factory.olin.data

import android.keyboard.engine.KeyRole
import kotlinx.coroutines.flow.Flow

class KeyboardProjectRepository(private val db: KeyboardFactoryDatabase) {

    fun search(query: String): Flow<List<KeyboardProjectEntity>> =
        if (query.isBlank()) db.keyboardProjectDao().observeAll() else db.keyboardProjectDao().search(query)

    fun pagesOf(projectId: Long): Flow<List<PageEntity>> = db.pageDao().observeForProject(projectId)

    fun cellsOf(pageId: Long): Flow<List<KeyCellEntity>> = db.keyCellDao().observeForPage(pageId)

    suspend fun createProject(name: String, firstPageRows: Int, firstPageCols: Int): Long {
        val now = System.currentTimeMillis()
        val projectId = db.keyboardProjectDao().insert(
            KeyboardProjectEntity(name = name, createdAt = now, updatedAt = now),
        )
        addPage(projectId, firstPageRows, firstPageCols)
        return projectId
    }

    suspend fun addPage(projectId: Long, rows: Int, cols: Int): Long {
        require(rows in 1..32) { "rows must be in 1..32" }
        require(cols in 1..32) { "cols must be in 1..32" }

        val nextIndex = db.pageDao().maxPageIndex(projectId) + 1
        val pageId = db.pageDao().insert(
            PageEntity(projectId = projectId, pageIndex = nextIndex, rows = rows, cols = cols),
        )
        val cells = (0 until rows).flatMap { r ->
            (0 until cols).map { c -> KeyCellEntity(pageId = pageId, row = r, col = c, role = KeyRole.NONE) }
        }
        db.keyCellDao().insertAll(cells)
        return pageId
    }

    suspend fun renameProject(projectId: Long, name: String) {
        val project = db.keyboardProjectDao().getById(projectId) ?: return
        db.keyboardProjectDao().update(project.copy(name = name, updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteProject(project: KeyboardProjectEntity) {
        db.keyboardProjectDao().delete(project)
    }
}
