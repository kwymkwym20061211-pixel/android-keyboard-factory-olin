package android.keyboard.factory.olin.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface KeyboardProjectDao {
    @Insert
    suspend fun insert(project: KeyboardProjectEntity): Long

    @Update
    suspend fun update(project: KeyboardProjectEntity)

    @Delete
    suspend fun delete(project: KeyboardProjectEntity)

    @Query("SELECT * FROM keyboard_project WHERE id = :id")
    suspend fun getById(id: Long): KeyboardProjectEntity?

    @Query("SELECT * FROM keyboard_project WHERE id = :id")
    fun observeById(id: Long): Flow<KeyboardProjectEntity?>

    @Query("SELECT * FROM keyboard_project WHERE name LIKE '%' || :query || '%' ORDER BY updatedAt DESC")
    fun search(query: String): Flow<List<KeyboardProjectEntity>>

    @Query("SELECT * FROM keyboard_project ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<KeyboardProjectEntity>>
}
