package android.keyboard.factory.olin.exportimport

import android.content.Context
import android.keyboard.factory.olin.data.KeyboardFactoryDatabase
import android.keyboard.factory.olin.data.KeyboardProjectEntity
import android.keyboard.factory.olin.data.KeyboardProjectRepository
import android.keyboard.factory.olin.data.PageEntity
import android.keyboard.factory.olin.data.groupCellsIntoKeyDefs
import android.keyboard.factory.olin.data.insertKeyDefsForPage
import android.keyboard.factory.olin.export.DownloadsWriter
import android.net.Uri
import androidx.room.withTransaction
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Reads/writes a project as a `.keyboard` file: a zip containing `keyboard.json` plus the
 * project's icon/font files under fixed entry names, for backing up or moving a project between
 * devices (distinct from [android.keyboard.factory.olin.export.KeyboardExportPipeline], which
 * builds an installable signed APK). */
object KeyboardProjectZipIo {

    data class ExportResult(val downloadsUri: Uri, val displayName: String)

    private const val ENTRY_MANIFEST = "keyboard.json"
    private const val ENTRY_ICON = "icon.png"
    private const val ENTRY_DEFAULT_FONT = "fonts/default.ttf"
    private val PAGE_FONT_ENTRY_REGEX = Regex("""fonts/page_(\d+)\.ttf""")

    private fun pageFontEntry(pageIndex: Int) = "fonts/page_$pageIndex.ttf"

    // A zip entry name is only ever used as a map key to compare against this fixed allow-list —
    // never as a filesystem path — so a malicious entry name can't escape the destination dir.
    private fun isKnownEntryName(name: String): Boolean =
        name == ENTRY_MANIFEST || name == ENTRY_ICON || name == ENTRY_DEFAULT_FONT || PAGE_FONT_ENTRY_REGEX.matches(name)

    private const val MAX_ENTRY_BYTES = 8L * 1024 * 1024
    private const val MAX_TOTAL_BYTES = 32L * 1024 * 1024

    suspend fun export(context: Context, projectId: Long): ExportResult {
        val db = KeyboardFactoryDatabase.getInstance(context)
        val project = db.keyboardProjectDao().getById(projectId) ?: error("Project not found: $projectId")
        val pages = db.pageDao().getForProject(projectId)
        check(pages.isNotEmpty()) { "Project has no pages" }

        val pageExports = pages.map { page ->
            PageExport(
                rows = page.rows,
                cols = page.cols,
                fontOverride = page.fontPathOverride?.let { pageFontEntry(page.pageIndex) },
                keys = groupCellsIntoKeyDefs(db.keyCellDao().getForPage(page.id)),
            )
        }
        val export = ProjectExport(
            name = project.name,
            icon = project.iconPath?.let { ENTRY_ICON },
            defaultFont = project.defaultFontPath?.let { ENTRY_DEFAULT_FONT },
            pages = pageExports,
        )

        val workDir = File(context.cacheDir, "export/project_zip").apply { mkdirs() }
        val zipFile = File(workDir, "$projectId.keyboard")
        ZipOutputStream(zipFile.outputStream()).use { zip ->
            writeEntry(zip, ENTRY_MANIFEST, ProjectExportJson.encode(export).toByteArray())
            project.iconPath?.let { writeFileEntry(zip, ENTRY_ICON, File(it)) }
            project.defaultFontPath?.let { writeFileEntry(zip, ENTRY_DEFAULT_FONT, File(it)) }
            for (page in pages) {
                page.fontPathOverride?.let { writeFileEntry(zip, pageFontEntry(page.pageIndex), File(it)) }
            }
        }

        val displayName = project.name.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".keyboard"
        val uri = DownloadsWriter.write(context, zipFile, displayName, "application/octet-stream")
        return ExportResult(uri, displayName)
    }

    suspend fun import(context: Context, sourceUri: Uri): Long {
        val entries = readKnownEntries(context, sourceUri)

        val manifestBytes = entries[ENTRY_MANIFEST] ?: error("Not a valid .keyboard file: missing $ENTRY_MANIFEST")
        val export = ProjectExportJson.decode(manifestBytes.toString(Charsets.UTF_8))
        require(export.formatVersion == PROJECT_EXPORT_FORMAT_VERSION) {
            "Unsupported .keyboard format version: ${export.formatVersion}"
        }
        require(export.pages.isNotEmpty()) { "Project has no pages" }

        val db = KeyboardFactoryDatabase.getInstance(context)
        val now = System.currentTimeMillis()

        val (projectId, pageIds) = db.withTransaction {
            val projectId = db.keyboardProjectDao().insert(KeyboardProjectEntity(name = export.name, createdAt = now, updatedAt = now))
            val pageIds = export.pages.mapIndexed { index, page ->
                val pageId = db.pageDao().insert(PageEntity(projectId = projectId, pageIndex = index, rows = page.rows, cols = page.cols))
                insertKeyDefsForPage(db.keyCellDao(), pageId, page.rows, page.cols, page.keys)
                pageId
            }
            projectId to pageIds
        }

        val repository = KeyboardProjectRepository(db)
        entries[ENTRY_ICON]?.let { bytes ->
            val file = writeImportedFile(context, "icons", "project_${projectId}_icon.png", bytes)
            repository.setIconPath(projectId, file.absolutePath)
        }
        entries[ENTRY_DEFAULT_FONT]?.let { bytes ->
            val file = writeImportedFile(context, "fonts", "project_${projectId}_default.ttf", bytes)
            repository.setDefaultFontPath(projectId, file.absolutePath)
        }
        pageIds.forEachIndexed { index, pageId ->
            val bytes = entries[pageFontEntry(index)] ?: return@forEachIndexed
            val file = writeImportedFile(context, "fonts", "page_${pageId}_override.ttf", bytes)
            repository.setPageFontOverride(pageId, file.absolutePath)
        }

        return projectId
    }

    private fun readKnownEntries(context: Context, sourceUri: Uri): Map<String, ByteArray> {
        val entries = mutableMapOf<String, ByteArray>()
        var totalRead = 0L
        val stream = context.contentResolver.openInputStream(sourceUri) ?: error("Could not open $sourceUri")
        stream.use { input ->
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (isKnownEntryName(entry.name)) {
                        val bytes = readBounded(zip, maxBytes = minOf(MAX_ENTRY_BYTES, MAX_TOTAL_BYTES - totalRead))
                        totalRead += bytes.size
                        entries[entry.name] = bytes
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
        return entries
    }

    // Bounds the read instead of trusting ZipEntry.size, which is attacker-supplied and can lie
    // (or be -1), to guard against a zip bomb hidden in a picked .keyboard file.
    private fun readBounded(input: InputStream, maxBytes: Long): ByteArray {
        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(8192)
        var total = 0L
        while (true) {
            val n = input.read(chunk)
            if (n < 0) break
            total += n
            require(total <= maxBytes) { "Zip entry exceeds the ${maxBytes}-byte limit" }
            buffer.write(chunk, 0, n)
        }
        return buffer.toByteArray()
    }

    private fun writeEntry(zip: ZipOutputStream, name: String, bytes: ByteArray) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(bytes)
        zip.closeEntry()
    }

    private fun writeFileEntry(zip: ZipOutputStream, name: String, file: File) {
        zip.putNextEntry(ZipEntry(name))
        file.inputStream().use { it.copyTo(zip) }
        zip.closeEntry()
    }

    private fun writeImportedFile(context: Context, subDir: String, fileName: String, bytes: ByteArray): File {
        val dir = File(context.filesDir, subDir).apply { mkdirs() }
        val file = File(dir, fileName)
        file.writeBytes(bytes)
        return file
    }
}
