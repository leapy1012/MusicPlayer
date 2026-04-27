package gd.app.musicplayer.feature.theme

import android.content.Context
import java.io.File

object ThemeBackgroundStore {

    fun createDraftBackgroundFile(context: Context): File {
        val file = File(themeDirectory(context), "theme_draft_${System.currentTimeMillis()}.jpg")
        file.parentFile?.mkdirs()
        return file
    }

    fun createSavedBackgroundFile(context: Context): File {
        val file = File(themeDirectory(context), "theme_custom_${System.currentTimeMillis()}.jpg")
        file.parentFile?.mkdirs()
        return file
    }

    fun isManagedThemePath(context: Context, path: String?): Boolean {
        if (path.isNullOrBlank()) return false
        val themeDir = themeDirectory(context).absolutePath
        return runCatching { File(path).canonicalPath.startsWith(File(themeDir).canonicalPath) }
            .getOrDefault(false)
    }

    fun isDraftThemePath(context: Context, path: String?): Boolean {
        if (!isManagedThemePath(context, path)) return false
        return File(path).name.startsWith("theme_draft_")
    }

    private fun themeDirectory(context: Context): File =
        File(context.filesDir, "theme")
}
