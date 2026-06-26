package android.keyboard.factory.olin.data

import android.keyboard.engine.KeyRole
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Every grid position of a page has exactly one row, from page creation onward. A merge is
 * expressed by pointing [ownerCellId] at another cell in the same page; only a cell with
 * [ownerCellId] == null (the topmost-then-leftmost of its merge group) carries [role]/[text]. */
@Entity(
    tableName = "key_cell",
    foreignKeys = [
        ForeignKey(
            entity = PageEntity::class,
            parentColumns = ["id"],
            childColumns = ["pageId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = KeyCellEntity::class,
            parentColumns = ["id"],
            childColumns = ["ownerCellId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("pageId"), Index("ownerCellId")],
)
data class KeyCellEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pageId: Long,
    val row: Int,
    val col: Int,
    val ownerCellId: Long? = null,
    val role: KeyRole = KeyRole.NONE,
    val text: String? = null,
)
