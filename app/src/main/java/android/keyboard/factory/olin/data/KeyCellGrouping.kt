package android.keyboard.factory.olin.data

import android.keyboard.engine.KeyDef
import android.keyboard.engine.KeyRole

/** Groups a page's flat cell rows into [KeyDef]s by merge-group ownership — the shape both
 * APK export ([android.keyboard.factory.olin.export.KeyboardExportPipeline]) and project export
 * ([android.keyboard.factory.olin.exportimport.KeyboardProjectZipIo]) need. */
fun groupCellsIntoKeyDefs(cells: List<KeyCellEntity>): List<KeyDef> {
    val byOwner = cells.groupBy { it.ownerCellId ?: it.id }
    return byOwner.map { (ownerId, members) ->
        val owner = cells.first { it.id == ownerId }
        KeyDef(ownedCells = members.map { listOf(it.row, it.col) }, role = owner.role, text = owner.text)
    }
}

/** Reconstructs a page's cells from imported [KeyDef]s. Never trusts the JSON's cell ordering or
 * an implied owner: re-elects the topmost-leftmost cell of each group as owner (the same
 * convention [KeyboardProjectRepository.resizePage] uses), since `ownerCellId` is a local DB id
 * that isn't portable across machines. Out-of-bounds or doubly-claimed coordinates are rejected
 * (hand-edited/malformed JSON is an explicit threat here), and any coordinate left unclaimed by
 * every [KeyDef] is filled in as a standalone NONE cell so the page always ends up with exactly
 * rows*cols cells, matching the invariant the rest of the app assumes. */
suspend fun insertKeyDefsForPage(keyCellDao: KeyCellDao, pageId: Long, rows: Int, cols: Int, keys: List<KeyDef>) {
    val claimed = mutableSetOf<Pair<Int, Int>>()

    for (key in keys) {
        require(key.ownedCells.isNotEmpty()) { "A key must own at least one cell" }
        val coords = key.ownedCells.map { it[0] to it[1] }.distinct()
        for (coord in coords) {
            require(coord.first in 0 until rows && coord.second in 0 until cols) {
                "Cell $coord is out of bounds for a $rows x $cols page"
            }
            require(claimed.add(coord)) { "Cell $coord is claimed by more than one key" }
        }

        val owner = coords.minWith(compareBy({ it.first }, { it.second }))
        val ownerId = keyCellDao.insertAll(
            listOf(KeyCellEntity(pageId = pageId, row = owner.first, col = owner.second, role = key.role, text = key.text)),
        ).first()

        val members = coords.filterNot { it == owner }.map { (r, c) ->
            KeyCellEntity(pageId = pageId, row = r, col = c, ownerCellId = ownerId, role = KeyRole.NONE, text = null)
        }
        if (members.isNotEmpty()) keyCellDao.insertAll(members)
    }

    val gaps = (0 until rows).flatMap { r -> (0 until cols).mapNotNull { c -> (r to c).takeIf { it !in claimed } } }
    if (gaps.isNotEmpty()) {
        keyCellDao.insertAll(gaps.map { (r, c) -> KeyCellEntity(pageId = pageId, row = r, col = c, role = KeyRole.NONE) })
    }
}
