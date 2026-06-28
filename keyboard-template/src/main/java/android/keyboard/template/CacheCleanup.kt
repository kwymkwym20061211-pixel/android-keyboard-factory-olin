package android.keyboard.template

import android.content.Context

/** Sweeps `cacheDir` once per process, gated by an in-memory flag rather than
 * [android.app.Application.onCreate] — in this app, process start can be triggered either by
 * [GeneratedKeyboardService][android.keyboard.template.GeneratedKeyboardService] opening the IME
 * or by an Activity opening, and only the latter should pay for this. [invalidate] resets the
 * flag so a future caller (e.g. an action that invalidates whatever's cached) can force the next
 * [clearOnce] to actually run again instead of being a no-op for the rest of the process. */
object CacheCleanup {
    @Volatile private var cleared = false

    fun clearOnce(context: Context) {
        if (cleared) return
        cleared = true
        val cacheDir = context.cacheDir
        Thread { cacheDir.listFiles()?.forEach { it.deleteRecursively() } }.start()
    }

    fun invalidate() {
        cleared = false
    }
}
