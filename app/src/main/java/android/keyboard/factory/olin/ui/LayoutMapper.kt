package android.keyboard.factory.olin.ui

import android.keyboard.engine.CellState
import android.keyboard.engine.KeyDef
import android.keyboard.engine.PageLayout
import android.keyboard.factory.olin.data.KeyCellEntity
import android.keyboard.factory.olin.data.PageEntity

fun toPageLayout(page: PageEntity, cells: List<KeyCellEntity>): PageLayout {
    val byOwner = cells.groupBy { it.ownerCellId ?: it.id }
    val keys = byOwner.map { (ownerId, members) ->
        val owner = cells.first { it.id == ownerId }
        KeyDef(
            ownedCells = members.map { listOf(it.row, it.col) },
            role = owner.role,
            text = owner.text,
            image = null,
        )
    }
    return PageLayout(rows = page.rows, cols = page.cols, keys = keys)
}

/** Finds the owner entity (the one whose row/col the [KeyDef] was rendered with persistence
 * identity for) among [cells] for a tapped [KeyDef]. */
fun ownerOf(key: KeyDef, cells: List<KeyCellEntity>): KeyCellEntity =
    cells.first { cell -> cell.ownerCellId == null && key.ownedCells.any { it[0] == cell.row && it[1] == cell.col } }

fun KeyCellEntity.toCellState() = CellState(id = id, row = row, col = col, ownerId = ownerCellId, role = role, text = text)

fun CellState.toEntity(pageId: Long) = KeyCellEntity(id = id, pageId = pageId, row = row, col = col, ownerCellId = ownerId, role = role, text = text)
