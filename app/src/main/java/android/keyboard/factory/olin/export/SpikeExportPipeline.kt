package android.keyboard.factory.olin.export

import android.content.Context
import android.net.Uri
import java.io.File

/** Phase-0 de-risking spike: exercises the same patch -> sign -> Downloads path that the real
 * per-project export (Phase 5) will use, but with a fixed signing alias and no real layout/font
 * content yet. */
object SpikeExportPipeline {

    private const val TEMPLATE_ASSET = "keyboard_template.apk"
    private const val SIGNING_ALIAS = "spike_project_key"

    data class Result(val downloadsUri: Uri, val displayName: String)

    fun run(context: Context, keyboardName: String, applicationId: String): Result {
        val workDir = File(context.cacheDir, "export").apply { mkdirs() }

        val templateFile = File(workDir, "template.apk")
        context.assets.open(TEMPLATE_ASSET).use { input ->
            templateFile.outputStream().use { input.copyTo(it) }
        }

        val patchedFile = File(workDir, "patched-unsigned.apk")
        TemplateApkPatcher.patch(
            templateApk = templateFile,
            outputApk = patchedFile,
            newPackageName = applicationId,
            newLabel = keyboardName,
        )

        val (privateKey, certificate) = ApkSigningKeys.getOrCreate(SIGNING_ALIAS)
        val signedFile = File(workDir, "signed.apk")
        ApkExporter.sign(patchedFile, signedFile, privateKey, certificate)

        val displayName = keyboardName.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".apk"
        val uri = DownloadsWriter.writeApk(context, signedFile, displayName)
        return Result(uri, displayName)
    }
}
