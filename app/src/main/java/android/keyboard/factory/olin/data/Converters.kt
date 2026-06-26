package android.keyboard.factory.olin.data

import android.keyboard.engine.KeyRole
import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromKeyRole(role: KeyRole): String = role.name

    @TypeConverter
    fun toKeyRole(value: String): KeyRole = KeyRole.valueOf(value)
}
