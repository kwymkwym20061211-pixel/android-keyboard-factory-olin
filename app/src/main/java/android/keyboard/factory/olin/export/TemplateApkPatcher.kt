package android.keyboard.factory.olin.export

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.reandroid.apk.ApkModule
import com.reandroid.archive.ByteInputSource
import java.io.ByteArrayOutputStream
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
        iconSourceFile: File? = null,
    ) {
        val apkModule = ApkModule.loadApkFile(templateApk)
        val manifest = apkModule.androidManifest
        manifest.packageName = newPackageName
        manifest.setApplicationLabel(newLabel)

        for ((path, bytes) in extraAssets) {
            runCatching { apkModule.removeInputSource(path) }
            apkModule.add(ByteInputSource(bytes, path))
        }

        if (iconSourceFile != null) {
            replaceLauncherIcon(apkModule, iconSourceFile)
        }

        apkModule.writeApk(outputApk)
    }

    /** Every density bucket the template ships under res/mipmap-.../ic_launcher already has a
     * placeholder PNG baked in at compile time (the resource table needs a real entry for
     * android:icon to point at). Each bucket's own existing pixel size tells us what size to
     * resize the project's icon to for that bucket, so there's no separate density→px table to
     * keep in sync here. */
    private fun replaceLauncherIcon(apkModule: ApkModule, iconSourceFile: File) {
        val sourceBitmap = BitmapFactory.decodeFile(iconSourceFile.path) ?: return

        val launcherIconResFiles = apkModule.listResFiles().filter { resFile ->
            val entry = resFile.pickOne()
            entry != null && entry.typeName == "mipmap" && entry.name == "ic_launcher"
        }

        for (resFile in launcherIconResFiles) {
            val placeholderBytes = resFile.inputSource.openStream().use { it.readBytes() }
            val placeholder = BitmapFactory.decodeByteArray(placeholderBytes, 0, placeholderBytes.size)
            val size = maxOf(placeholder.width, placeholder.height)

            val resized = Bitmap.createScaledBitmap(sourceBitmap, size, size, true)
            val output = ByteArrayOutputStream()
            resized.compress(Bitmap.CompressFormat.PNG, 100, output)

            val path = resFile.filePath
            apkModule.removeInputSource(path)
            apkModule.add(ByteInputSource(output.toByteArray(), path))
        }
    }
}
