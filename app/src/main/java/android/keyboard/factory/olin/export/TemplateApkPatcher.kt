package android.keyboard.factory.olin.export

import com.reandroid.apk.ApkModule
import com.reandroid.archive.ByteInputSource
import java.io.File

/** Rewrites the package name and app label of a prebuilt template APK, and injects/overwrites
 * asset entries (layout JSON, key glyph PNGs) — all at the ZIP/binary-XML level via ARSCLib,
 * with no aapt/d8 recompilation involved. */
object TemplateApkPatcher {

    fun patch(
        templateApk: File,
        outputApk: File,
        newPackageName: String,
        newLabel: String,
        extraAssets: Map<String, ByteArray> = emptyMap(),
    ) {
        val apkModule = ApkModule.loadApkFile(templateApk)
        val manifest = apkModule.androidManifest
        manifest.packageName = newPackageName
        manifest.setApplicationLabel(newLabel)

        for ((path, bytes) in extraAssets) {
            runCatching { apkModule.removeInputSource(path) }
            apkModule.add(ByteInputSource(bytes, path))
        }

        apkModule.writeApk(outputApk)
    }
}
