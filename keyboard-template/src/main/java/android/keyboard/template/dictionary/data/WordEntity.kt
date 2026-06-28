package android.keyboard.template.dictionary.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "words",
    foreignKeys = [
        ForeignKey(
            entity = DictionaryEntity::class,
            parentColumns = ["id"],
            childColumns = ["dictionaryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["dictionaryId", "reading"])],
)
data class WordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dictionaryId: Long,
    val reading: String,
    val target: String,
)
