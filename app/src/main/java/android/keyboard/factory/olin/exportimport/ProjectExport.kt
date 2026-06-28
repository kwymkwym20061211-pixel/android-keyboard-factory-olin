package android.keyboard.factory.olin.exportimport

import android.keyboard.engine.KeyDef
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

const val PROJECT_EXPORT_FORMAT_VERSION = 1

/** The portable contents of a `.keyboard` file's `keyboard.json` entry. Deliberately omits
 * `applicationId`/`signingKeyAlias`/timestamps from [android.keyboard.factory.olin.data.KeyboardProjectEntity]
 * — those stay fixed to this device's keystore for the life of a project, so an imported project
 * must always be treated as fresh rather than inheriting another device's identity. */
@Serializable
data class ProjectExport(
    val formatVersion: Int = PROJECT_EXPORT_FORMAT_VERSION,
    val name: String,
    val icon: String? = null,
    val defaultFont: String? = null,
    val pages: List<PageExport>,
)

/** [keys] reuses [KeyDef] as-is; its `image` field is always left null since glyph PNGs are a
 * render product regenerated at APK-export time, not project source data. */
@Serializable
data class PageExport(
    val rows: Int,
    val cols: Int,
    val fontOverride: String? = null,
    val keys: List<KeyDef>,
)

object ProjectExportJson {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
    fun encode(export: ProjectExport): String = json.encodeToString(export)
    fun decode(text: String): ProjectExport = json.decodeFromString(text)
}
