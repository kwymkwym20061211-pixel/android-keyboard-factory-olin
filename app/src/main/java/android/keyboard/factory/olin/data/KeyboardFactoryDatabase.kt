package android.keyboard.factory.olin.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [KeyboardProjectEntity::class, PageEntity::class, KeyCellEntity::class],
    version = 2,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class KeyboardFactoryDatabase : RoomDatabase() {
    abstract fun keyboardProjectDao(): KeyboardProjectDao
    abstract fun pageDao(): PageDao
    abstract fun keyCellDao(): KeyCellDao

    companion object {
        @Volatile private var instance: KeyboardFactoryDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE keyboard_project ADD COLUMN iconPath TEXT")
            }
        }

        fun getInstance(context: Context): KeyboardFactoryDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    KeyboardFactoryDatabase::class.java,
                    "keyboard_factory.db",
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
    }
}
