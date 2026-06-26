package android.keyboard.engine

import kotlinx.serialization.Serializable

enum class KeyRole {
    CHAR,
    ENTER,
    DELETE,
    PAGE_NEXT,
    PAGE_PREV,
    NONE,
}

/**
 * [ownedCells] is the full set of (row, col) grid coordinates this key occupies, including
 * possibly non-rectangular (staircase/zigzag) merges. The exported owner cell is always the
 * topmost-then-leftmost cell of the group.
 */
@Serializable
data class KeyDef(
    val ownedCells: List<List<Int>>,
    val role: KeyRole,
    val text: String? = null,
    val image: String? = null,
)

@Serializable
data class PageLayout(
    val rows: Int,
    val cols: Int,
    val keys: List<KeyDef>,
)

@Serializable
data class KeyboardLayout(
    val schemaVersion: Int,
    val pages: List<PageLayout>,
)
