package android.keyboard.factory.olin.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface KeyCellDao {
    @Insert
    suspend fun insertAll(cells: List<KeyCellEntity>): List<Long>

    @Update
    suspend fun update(cell: KeyCellEntity)

    @Update
    suspend fun updateAll(cells: List<KeyCellEntity>)

    @Query("SELECT * FROM key_cell WHERE pageId = :pageId ORDER BY row, col")
    fun observeForPage(pageId: Long): Flow<List<KeyCellEntity>>

    @Query("SELECT * FROM key_cell WHERE pageId = :pageId ORDER BY row, col")
    suspend fun getForPage(pageId: Long): List<KeyCellEntity>
}
