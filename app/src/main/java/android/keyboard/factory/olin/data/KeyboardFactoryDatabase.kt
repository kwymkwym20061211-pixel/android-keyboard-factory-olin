package android.keyboard.factory.olin.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [KeyboardProjectEntity::class, PageEntity::class, KeyCellEntity::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class KeyboardFactoryDatabase : RoomDatabase() {
    abstract fun keyboardProjectDao(): KeyboardProjectDao
    abstract fun pageDao(): PageDao
    abstract fun keyCellDao(): KeyCellDao

    companion object {
        @Volatile private var instance: KeyboardFactoryDatabase? = null

        fun getInstance(context: Context): KeyboardFactoryDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    KeyboardFactoryDatabase::class.java,
                    "keyboard_factory.db",
                ).build().also { instance = it }
            }
    }
}
