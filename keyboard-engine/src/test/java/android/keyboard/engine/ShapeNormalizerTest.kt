package android.keyboard.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShapeNormalizerTest {

    private fun grid(rows: Int, cols: Int): List<CellState> =
        (0 until rows).flatMap { r -> (0 until cols).map { c -> CellState(id = (r * cols + c).toLong(), row = r, col = c) } }

    @Test
    fun `merge two adjacent cells elects topmost-left owner and keeps active role`() {
        val cells = grid(2, 2).map {
            if (it.id == 0L) it.copy(role = KeyRole.CHAR, text = "a") else it
        }
        val result = ShapeNormalizer.mergeInDirection(cells, activeCellId = 0L, direction = Direction.RIGHT)

        val owner = result.first { it.id == 0L }
        val absorbed = result.first { it.id == 1L }
        assertNull(owner.ownerId)
        assertEquals(KeyRole.CHAR, owner.role)
        assertEquals("a", owner.text)
        assertEquals(0L, absorbed.ownerId)
        assertEquals(KeyRole.NONE, absorbed.role)
    }

    @Test
    fun `merging into an existing group re-elects topmost-left owner across all members`() {
        // 2x2 grid: ids 0,1 / 2,3. First merge 0+1 (row0), then merge that group with 2 (below 0).
        var cells = grid(2, 2)
        cells = ShapeNormalizer.mergeInDirection(cells, activeCellId = 0L, direction = Direction.RIGHT)
        cells = ShapeNormalizer.mergeInDirection(cells, activeCellId = 0L, direction = Direction.DOWN)

        val owner = cells.first { it.id == 0L }
        assertNull(owner.ownerId)
        val members = cells.filter { (it.ownerId ?: it.id) == 0L }
        assertEquals(setOf(0L, 1L, 2L), members.map { it.id }.toSet())
    }

    @Test
    fun `merging already-merged group is a no-op`() {
        var cells = grid(1, 2)
        cells = ShapeNormalizer.mergeInDirection(cells, activeCellId = 0L, direction = Direction.RIGHT)
        val again = ShapeNormalizer.mergeInDirection(cells, activeCellId = 0L, direction = Direction.RIGHT)
        assertEquals(cells, again)
    }

    @Test
    fun `merge off-grid direction is a no-op`() {
        val cells = grid(2, 2)
        val result = ShapeNormalizer.mergeInDirection(cells, activeCellId = 0L, direction = Direction.UP)
        assertEquals(cells, result)
    }

    @Test
    fun `unmerge splits a member back out and preserves owner role on the remaining group`() {
        var cells = grid(1, 3).map { if (it.id == 0L) it.copy(role = KeyRole.ENTER) else it }
        cells = ShapeNormalizer.mergeInDirection(cells, activeCellId = 0L, direction = Direction.RIGHT) // 0+1
        // Extend the group by merging from cell 1 (the edge of the current group) into cell 2.
        cells = ShapeNormalizer.mergeInDirection(cells, activeCellId = 1L, direction = Direction.RIGHT)

        cells = ShapeNormalizer.unmerge(cells, cellId = 1L)

        val splitOut = cells.first { it.id == 1L }
        assertNull(splitOut.ownerId)
        assertEquals(KeyRole.NONE, splitOut.role)

        val remainingOwner = cells.first { it.id == 0L }
        assertNull(remainingOwner.ownerId)
        assertEquals(KeyRole.ENTER, remainingOwner.role)
        val remainingMember = cells.first { it.id == 2L }
        assertEquals(0L, remainingMember.ownerId)
    }

    @Test
    fun `unmerge can split the group owner itself, re-electing a new owner among survivors`() {
        var cells = grid(1, 3).map { if (it.id == 0L) it.copy(role = KeyRole.ENTER) else it }
        cells = ShapeNormalizer.mergeInDirection(cells, activeCellId = 0L, direction = Direction.RIGHT) // 0+1
        cells = ShapeNormalizer.mergeInDirection(cells, activeCellId = 1L, direction = Direction.RIGHT) // +2

        // Splitting the owner (0) itself must not be a no-op: 1 should become the new owner.
        cells = ShapeNormalizer.unmerge(cells, cellId = 0L)

        val splitOut = cells.first { it.id == 0L }
        assertNull(splitOut.ownerId)
        assertEquals(KeyRole.NONE, splitOut.role)

        val newOwner = cells.first { it.id == 1L }
        assertNull(newOwner.ownerId)
        assertEquals(KeyRole.ENTER, newOwner.role)
        assertEquals(1L, cells.first { it.id == 2L }.ownerId)
    }

    @Test
    fun `unmerge on an already-standalone cell is a no-op`() {
        val cells = grid(2, 2)
        val result = ShapeNormalizer.unmerge(cells, cellId = 0L)
        assertEquals(cells, result)
    }

    @Test
    fun `an L-shaped merge of b never touches the unrelated a cell`() {
        // Layout (2x2): a b / b b -- ids 0=a(0,0) 1=b(0,1) 2=b(1,0) 3=b(1,1)
        var cells = grid(2, 2).mapIndexed { index, cell ->
            when (index.toLong()) {
                0L -> cell.copy(role = KeyRole.CHAR, text = "a")
                else -> cell.copy(role = KeyRole.CHAR, text = "b")
            }
        }
        // Merge the three b-cells (1,2,3) together without ever touching cell 0.
        cells = ShapeNormalizer.mergeInDirection(cells, activeCellId = 1L, direction = Direction.DOWN) // 1+3
        cells = ShapeNormalizer.mergeInDirection(cells, activeCellId = 3L, direction = Direction.LEFT) // +2

        val a = cells.first { it.id == 0L }
        assertNull(a.ownerId)
        assertEquals("a", a.text)

        val bOwner = cells.first { it.id == 1L }
        assertNull(bOwner.ownerId)
        assertEquals("b", bOwner.text)
        assertEquals(setOf(1L, 2L, 3L), cells.filter { (it.ownerId ?: it.id) == 1L }.map { it.id }.toSet())
    }
}
