package android.keyboard.template.dictionary.data

import android.keyboard.template.dictionary.csv.DictionaryCsv
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow

class DictionaryRepository(private val db: DictionaryDatabase) {

    fun observeDictionaries(): Flow<List<DictionaryEntity>> = db.dictionaryDao().observeAll()

    fun observeWords(dictionaryId: Long): Flow<List<WordEntity>> = db.wordDao().observeForDictionary(dictionaryId)

    suspend fun createDictionary(name: String): Result<Long> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return Result.failure(DictionaryError.EmptyName)
        DictionaryCsv.invalidFileNameChar(trimmed)?.let {
            return Result.failure(DictionaryError.InvalidNameChar(it))
        }
        if (db.dictionaryDao().findByName(trimmed) != null) {
            return Result.failure(DictionaryError.DuplicateName(trimmed))
        }
        return Result.success(db.dictionaryDao().insert(DictionaryEntity(name = trimmed)))
    }

    suspend fun deleteDictionary(dictionary: DictionaryEntity) {
        db.dictionaryDao().delete(dictionary)
    }

    suspend fun addWord(dictionaryId: Long, reading: String, target: String): Result<Long> {
        if (reading.isBlank() || target.isBlank()) {
            return Result.failure(DictionaryError.EmptyWordFields)
        }
        return Result.success(db.wordDao().insert(WordEntity(dictionaryId = dictionaryId, reading = reading, target = target)))
    }

    suspend fun updateWord(word: WordEntity) {
        db.wordDao().update(word)
    }

    suspend fun deleteWord(word: WordEntity) {
        db.wordDao().delete(word)
    }

    /** Validates filename/format/name-collision and, only if everything passes, inserts the new
     * dictionary and all its words in one transaction (no partial import on failure). */
    suspend fun importCsv(fileName: String, content: String): Result<Long> {
        val name = DictionaryCsv.dictionaryNameFromFileName(fileName)
            ?: return Result.failure(DictionaryError.InvalidCsvFileName)
        DictionaryCsv.invalidFileNameChar(name)?.let {
            return Result.failure(DictionaryError.InvalidNameChar(it))
        }
        if (db.dictionaryDao().findByName(name) != null) {
            return Result.failure(DictionaryError.DuplicateName(name))
        }
        val rows = when (val result = DictionaryCsv.decode(content)) {
            is DictionaryCsv.ParseResult.Error -> return Result.failure(DictionaryError.CsvParseError(result.lineNumber, result.reason))
            is DictionaryCsv.ParseResult.Success -> result.rows
        }
        val dictionaryId = db.withTransaction {
            val id = db.dictionaryDao().insert(DictionaryEntity(name = name))
            db.wordDao().insertAll(rows.map { (reading, target) -> WordEntity(dictionaryId = id, reading = reading, target = target) })
            id
        }
        return Result.success(dictionaryId)
    }

    suspend fun exportCsv(dictionaryId: Long): String {
        val words = db.wordDao().getAllForDictionary(dictionaryId)
        return DictionaryCsv.encode(words.map { it.reading to it.target })
    }

    /** Prefix-match candidate lookup across all dictionaries, used while typing. [prefix]'s SQL
     * LIKE wildcard characters are escaped first since it's arbitrary user-typed text. */
    suspend fun candidatesForPrefix(prefix: String, limit: Int = 20): List<WordEntity> {
        if (prefix.isEmpty()) return emptyList()
        return db.wordDao().searchByPrefixAcrossAll(escapeForSqlLike(prefix), limit)
    }

    private fun escapeForSqlLike(value: String): String =
        value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
}
