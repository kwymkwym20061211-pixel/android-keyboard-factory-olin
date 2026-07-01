package android.keyboard.factory.olin.data

import android.keyboard.engine.KeyRole
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow

class KeyboardProjectRepository(private val db: KeyboardFactoryDatabase) {

    fun search(query: String): Flow<List<KeyboardProjectEntity>> =
        if (query.isBlank()) db.keyboardProjectDao().observeAll() else db.keyboardProjectDao().search(query)

    fun observeProject(projectId: Long): Flow<KeyboardProjectEntity?> = db.keyboardProjectDao().observeById(projectId)

    suspend fun getProject(projectId: Long): KeyboardProjectEntity? = db.keyboardProjectDao().getById(projectId)

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

    /** Changes a page's grid dimensions in place. Cells that fall outside the new bounds are
     * removed; if a removed cell was the owner of a merge group that still has surviving
     * members, a new topmost-leftmost owner is elected among the survivors first (so Room's
     * cascading delete on the self-referencing FK never wipes out cells that are still in
     * bounds). Newly exposed cells (when growing) are seeded as standalone NONE cells. */
    suspend fun resizePage(pageId: Long, newRows: Int, newCols: Int) {
        require(newRows in 1..32) { "rows must be in 1..32" }
        require(newCols in 1..32) { "cols must be in 1..32" }

        db.withTransaction {
            val page = db.pageDao().getById(pageId) ?: return@withTransaction
            val cells = db.keyCellDao().getForPage(pageId)
            val byOwner = cells.groupBy { it.ownerCellId ?: it.id }

            val toUpdate = mutableListOf<KeyCellEntity>()
            val toDelete = mutableListOf<KeyCellEntity>()

            for ((ownerId, members) in byOwner) {
                val owner = cells.first { it.id == ownerId }
                val surviving = members.filter { it.row < newRows && it.col < newCols }
                val removed = members.filter { it.row >= newRows || it.col >= newCols }
                toDelete += removed
                if (surviving.isEmpty()) continue

                val newOwner = surviving.minWith(compareBy({ it.row }, { it.col }))
                for (member in surviving) {
                    toUpdate += if (member.id == newOwner.id) {
                        member.copy(ownerCellId = null, role = owner.role, text = owner.text)
                    } else {
                        member.copy(ownerCellId = newOwner.id, role = KeyRole.NONE, text = null)
                    }
                }
            }

            val existingCoords = cells.map { it.row to it.col }.toSet()
            val toInsert = (0 until newRows).flatMap { r ->
                (0 until newCols).mapNotNull { c ->
                    if ((r to c) in existingCoords) null else KeyCellEntity(pageId = pageId, row = r, col = c, role = KeyRole.NONE)
                }
            }

            if (toUpdate.isNotEmpty()) db.keyCellDao().updateAll(toUpdate)
            if (toDelete.isNotEmpty()) db.keyCellDao().deleteAll(toDelete)
            if (toInsert.isNotEmpty()) db.keyCellDao().insertAll(toInsert)
            db.pageDao().update(page.copy(rows = newRows, cols = newCols))
        }
    }

    suspend fun setPageFontOverride(pageId: Long, fontPath: String?) {
        val page = db.pageDao().getById(pageId) ?: return
        db.pageDao().update(page.copy(fontPathOverride = fontPath))
    }

    suspend fun renameProject(projectId: Long, name: String) {
        val project = db.keyboardProjectDao().getById(projectId) ?: return
        db.keyboardProjectDao().update(project.copy(name = name, updatedAt = System.currentTimeMillis()))
    }

    suspend fun setDefaultFontPath(projectId: Long, fontPath: String?) {
        val project = db.keyboardProjectDao().getById(projectId) ?: return
        db.keyboardProjectDao().update(project.copy(defaultFontPath = fontPath, updatedAt = System.currentTimeMillis()))
    }

    suspend fun setIconPath(projectId: Long, iconPath: String?) {
        val project = db.keyboardProjectDao().getById(projectId) ?: return
        db.keyboardProjectDao().update(project.copy(iconPath = iconPath, updatedAt = System.currentTimeMillis()))
    }

    suspend fun deletePage(pageId: Long): Boolean {
        val page = db.pageDao().getById(pageId) ?: return false
        val pages = db.pageDao().getForProject(page.projectId)
        if (pages.size <= 1) return false
        db.pageDao().delete(page)
        return true
    }

    suspend fun deleteProject(project: KeyboardProjectEntity) {
        db.keyboardProjectDao().delete(project)
    }
}
