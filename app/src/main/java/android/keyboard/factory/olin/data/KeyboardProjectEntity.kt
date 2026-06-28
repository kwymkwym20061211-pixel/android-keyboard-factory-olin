package android.keyboard.factory.olin.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** [applicationId] and [signingKeyAlias] are null until the first export, then stay fixed for
 * the life of the project so re-exports overwrite-install the same app. [defaultFontPath] and
 * [iconPath] are absolute paths into this app's private storage (fonts/images are copied out of
 * their SAF/Photo Picker content:// Uri at import time so they survive permission/URI churn). */
@Entity(tableName = "keyboard_project")
data class KeyboardProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val applicationId: String? = null,
    val signingKeyAlias: String? = null,
    val defaultFontPath: String? = null,
    val iconPath: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val lastExportedAt: Long? = null,
)
