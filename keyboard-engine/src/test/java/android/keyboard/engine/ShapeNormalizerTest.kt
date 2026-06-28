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
    fun `unmerge releases every member of the group back to standalone cells, keeping the owner's role on its own cell`() {
        var cells = grid(1, 3).map { if (it.id == 0L) it.copy(role = KeyRole.ENTER) else it }
        cells = ShapeNormalizer.mergeInDirection(cells, activeCellId = 0L, direction = Direction.RIGHT) // 0+1
        cells = ShapeNormalizer.mergeInDirection(cells, activeCellId = 1L, direction = Direction.RIGHT) // +2

        cells = ShapeNormalizer.unmerge(cells, cellId = 1L) // releasing from any member dissolves the whole group

        cells.forEach { assertNull(it.ownerId) }
        val owner = cells.first { it.id == 0L }
        assertEquals(KeyRole.ENTER, owner.role)
        assertEquals(KeyRole.NONE, cells.first { it.id == 1L }.role)
        assertEquals(KeyRole.NONE, cells.first { it.id == 2L }.role)
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
