package android.keyboard.engine

enum class Direction { UP, DOWN, LEFT, RIGHT }

/** Room-independent snapshot of one grid cell, used as the input/output of [ShapeNormalizer] so
 * the merge algorithm can be unit-tested without any database or Android dependency. */
data class CellState(
    val id: Long,
    val row: Int,
    val col: Int,
    val ownerId: Long? = null,
    val role: KeyRole = KeyRole.NONE,
    val text: String? = null,
)

/** Implements the merge normalization rule: the topmost-then-leftmost cell of a merge group is
 * always the "owner" that carries [KeyRole]/text; every other member only records which owner it
 * belongs to. Merging/un-merging re-elects the owner from scratch so shapes stay consistent
 * however many times they're edited (including non-rectangular staircase/zigzag merges). */
object ShapeNormalizer {

    fun mergeInDirection(cells: List<CellState>, activeCellId: Long, direction: Direction): List<CellState> {
        val byId = cells.associateBy { it.id }
        val active = byId[activeCellId] ?: return cells

        val (dRow, dCol) = when (direction) {
            Direction.UP -> -1 to 0
            Direction.DOWN -> 1 to 0
            Direction.LEFT -> 0 to -1
            Direction.RIGHT -> 0 to 1
        }
        val neighbor = cells.firstOrNull { it.row == active.row + dRow && it.col == active.col + dCol }
            ?: return cells

        fun ownerOf(cell: CellState) = cell.ownerId?.let { byId[it] } ?: cell

        val ownerActive = ownerOf(active)
        val ownerNeighbor = ownerOf(neighbor)
        if (ownerActive.id == ownerNeighbor.id) return cells

        val mergedIds = cells.filter { ownerOf(it).id == ownerActive.id || ownerOf(it).id == ownerNeighbor.id }
            .map { it.id }
            .toSet()
        val newOwner = cells.filter { it.id in mergedIds }.minWithOrNull(compareBy({ it.row }, { it.col }))!!

        return cells.map { cell ->
            when {
                cell.id !in mergedIds -> cell
                cell.id == newOwner.id -> cell.copy(ownerId = null, role = ownerActive.role, text = ownerActive.text)
                else -> cell.copy(ownerId = newOwner.id, role = KeyRole.NONE, text = null)
            }
        }
    }

    /** Splits [cellId] out of whatever merge group it belongs to, leaving it as a standalone
     * NONE-role cell. The remaining group (if any) re-elects a topmost-leftmost owner. Has no
     * effect if [cellId] is already an unmerged owner. */
    fun unmerge(cells: List<CellState>, cellId: Long): List<CellState> {
        val byId = cells.associateBy { it.id }
        val target = byId[cellId] ?: return cells
        val ownerId = target.ownerId ?: return cells
        val owner = byId[ownerId] ?: return cells

        val remaining = cells.filter { (it.ownerId ?: it.id) == ownerId && it.id != cellId }
        val newOwner = remaining.minWithOrNull(compareBy({ it.row }, { it.col }))

        return cells.map { cell ->
            when {
                cell.id == cellId -> cell.copy(ownerId = null, role = KeyRole.NONE, text = null)
                newOwner != null && cell.id == newOwner.id -> cell.copy(ownerId = null, role = owner.role, text = owner.text)
                newOwner != null && (cell.ownerId ?: cell.id) == ownerId -> cell.copy(ownerId = newOwner.id)
                else -> cell
            }
        }
    }
}
