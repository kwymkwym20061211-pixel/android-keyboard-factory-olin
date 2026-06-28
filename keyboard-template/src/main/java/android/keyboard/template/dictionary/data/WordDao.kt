package android.keyboard.template.dictionary.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WordDao {
    @Insert
    suspend fun insert(word: WordEntity): Long

    @Insert
    suspend fun insertAll(words: List<WordEntity>)

    @Update
    suspend fun update(word: WordEntity)

    @Delete
    suspend fun delete(word: WordEntity)

    @Query("SELECT * FROM words WHERE dictionaryId = :dictionaryId ORDER BY id")
    fun observeForDictionary(dictionaryId: Long): Flow<List<WordEntity>>

    @Query("SELECT * FROM words WHERE dictionaryId = :dictionaryId ORDER BY id")
    suspend fun getAllForDictionary(dictionaryId: Long): List<WordEntity>

    /** Prefix-match search across ALL dictionaries combined (typing-time candidate lookup has
     * no notion of a "currently selected" dictionary — that concept only applies to the editing
     * UI). [escapedPrefix] must already have `\`/`%`/`_` escaped by the caller. */
    @Query("SELECT * FROM words WHERE reading LIKE :escapedPrefix || '%' ESCAPE '\\' ORDER BY reading LIMIT :limit")
    suspend fun searchByPrefixAcrossAll(escapedPrefix: String, limit: Int): List<WordEntity>
}
