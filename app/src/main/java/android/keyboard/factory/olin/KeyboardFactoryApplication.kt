package android.keyboard.factory.olin

import android.app.Application

/** Nothing the app needs to keep running ever belongs in `cacheDir` — that's the whole point of
 * the directory — so a full sweep on every process start is safe. This is what clears the work
 * files [KeyboardExportPipeline][android.keyboard.factory.olin.export.KeyboardExportPipeline] and
 * [KeyboardProjectZipIo][android.keyboard.factory.olin.exportimport.KeyboardProjectZipIo] leave
 * behind after writing their real output to Downloads. */
class KeyboardFactoryApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Thread { cacheDir.listFiles()?.forEach { it.deleteRecursively() } }.start()
    }
}
