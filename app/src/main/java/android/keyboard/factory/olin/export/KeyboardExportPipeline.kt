package android.keyboard.factory.olin.export

import android.content.Context
import android.keyboard.engine.KeyDef
import android.keyboard.engine.KeyRole
import android.keyboard.engine.KeyboardLayout
import android.keyboard.engine.LayoutJson
import android.keyboard.engine.PageLayout
import android.keyboard.factory.olin.data.KeyboardFactoryDatabase
import android.keyboard.factory.olin.font.GlyphRenderer
import android.net.Uri
import java.io.File

/** The real per-project export: builds the layout JSON + per-key glyph PNGs straight from Room,
 * patches them into the template APK, signs with the project's persistent key, and writes the
 * result to Downloads. This is what the Phase 0 spike pipeline was de-risking. */
object KeyboardExportPipeline {

    private const val TEMPLATE_ASSET = "keyboard_template.apk"

    data class Result(val downloadsUri: Uri, val displayName: String)

    suspend fun export(context: Context, projectId: Long): Result {
        val db = KeyboardFactoryDatabase.getInstance(context)
        val project = db.keyboardProjectDao().getById(projectId) ?: error("Project not found: $projectId")
        val pages = db.pageDao().getForProject(projectId)
        check(pages.isNotEmpty()) { "Project has no pages" }

        val applicationId = project.applicationId ?: PackageNameGenerator.generate(project.id, project.name)
        val signingKeyAlias = project.signingKeyAlias ?: "project_key_${project.id}"

        val workDir = File(context.cacheDir, "export/${project.id}").apply { mkdirs() }

        val extraAssets = mutableMapOf<String, ByteArray>()
        val enginePages = pages.map { page -> buildPageLayout(db, page, project.defaultFontPath, extraAssets) }

        val layout = KeyboardLayout(schemaVersion = 1, pages = enginePages)
        extraAssets["assets/keyboard_layout.json"] = LayoutJson.encode(layout).toByteArray()

        val templateFile = File(workDir, "template.apk")
        context.assets.open(TEMPLATE_ASSET).use { input -> templateFile.outputStream().use { input.copyTo(it) } }

        val patchedFile = File(workDir, "patched-unsigned.apk")
        TemplateApkPatcher.patch(
            templateApk = templateFile,
            outputApk = patchedFile,
            newPackageName = applicationId,
            newLabel = project.name,
            extraAssets = extraAssets,
            iconSourceFile = project.iconPath?.let(::File),
        )

        val (privateKey, certificate) = ApkSigningKeys.getOrCreate(signingKeyAlias)
        val signedFile = File(workDir, "signed.apk")
        ApkExporter.sign(patchedFile, signedFile, privateKey, certificate)

        val displayName = project.name.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".apk"
        val uri = DownloadsWriter.writeApk(context, signedFile, displayName)

        val now = System.currentTimeMillis()
        db.keyboardProjectDao().update(
            project.copy(
                applicationId = applicationId,
                signingKeyAlias = signingKeyAlias,
                lastExportedAt = now,
                updatedAt = now,
            ),
        )

        return Result(uri, displayName)
    }

    private suspend fun buildPageLayout(
        db: KeyboardFactoryDatabase,
        page: android.keyboard.factory.olin.data.PageEntity,
        projectDefaultFontPath: String?,
        extraAssets: MutableMap<String, ByteArray>,
    ): PageLayout {
        val cells = db.keyCellDao().getForPage(page.id)
        val fontFile = (page.fontPathOverride ?: projectDefaultFontPath)?.let { File(it) }
        val byOwner = cells.groupBy { it.ownerCellId ?: it.id }

        val keys = byOwner.map { (ownerId, members) ->
            val owner = cells.first { it.id == ownerId }
            val imageFileName = if (owner.role == KeyRole.CHAR && !owner.text.isNullOrEmpty()) {
                "p${page.pageIndex}_${owner.row}_${owner.col}.png".also { fileName ->
                    extraAssets["assets/key_images/$fileName"] = GlyphRenderer.renderPng(owner.text, fontFile)
                }
            } else {
                null
            }
            KeyDef(
                ownedCells = members.map { listOf(it.row, it.col) },
                role = owner.role,
                text = owner.text,
                image = imageFileName,
            )
        }

        return PageLayout(rows = page.rows, cols = page.cols, keys = keys)
    }
}
