package android.keyboard.template.dictionary.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DictionaryDao {
    @Insert
    suspend fun insert(dictionary: DictionaryEntity): Long

    @Update
    suspend fun update(dictionary: DictionaryEntity)

    @Delete
    suspend fun delete(dictionary: DictionaryEntity)

    @Query("SELECT * FROM dictionaries WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): DictionaryEntity?

    @Query("SELECT * FROM dictionaries WHERE id = :id")
    suspend fun getById(id: Long): DictionaryEntity?

    @Query("SELECT * FROM dictionaries ORDER BY name")
    fun observeAll(): Flow<List<DictionaryEntity>>
}
