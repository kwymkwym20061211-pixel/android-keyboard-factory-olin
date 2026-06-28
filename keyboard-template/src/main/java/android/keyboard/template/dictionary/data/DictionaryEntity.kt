package android.keyboard.template.dictionary.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "dictionaries", indices = [Index(value = ["name"], unique = true)])
data class DictionaryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)
