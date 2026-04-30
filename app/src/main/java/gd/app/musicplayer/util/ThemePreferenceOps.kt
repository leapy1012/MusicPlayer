package gd.app.musicplayer.util

interface ThemePreferenceOps : PreferenceAccess {

    companion object {
        const val DEFAULT_THEME_IMAGE = "nature_01.webp"
        const val KEY_THEME_SKIN_URIS = "skin_uris"
        const val KEY_THEME_OVERLAY_COLOR = "theme_overlay_color"
        const val KEY_THEME_BLUR = "theme_blur"
        const val KEY_THEME_COLOR = "theme_color"
        const val KEY_THEME_DIALOG = "theme_dialog"
        const val KEY_IMAGE_NAME = "image_name"
        private const val SKIN_URI_SEPARATOR = "&&"
    }

    fun getThemeOverlayColor(): Int =
        getIntPreference(KEY_THEME_OVERLAY_COLOR, 855638016)

    fun setThemeOverlayColor(overlay: Int) {
        putIntPreference(KEY_THEME_OVERLAY_COLOR, overlay)
    }

    fun getThemeBlur(): Int =
        getIntPreference(KEY_THEME_BLUR, 0)

    fun setThemeBlur(blur: Int) {
        putIntPreference(KEY_THEME_BLUR, blur)
    }

    fun getThemeColor(): Int =
        getIntPreference(KEY_THEME_COLOR, -12467)

    fun setThemeColor(color: Int) {
        putIntPreference(KEY_THEME_COLOR, color)
    }

    fun isThemeDialogEnabled(): Boolean =
        getBooleanPreference(KEY_THEME_DIALOG, true)

    fun setThemeDialogEnabled(enabled: Boolean) {
        putBooleanPreference(KEY_THEME_DIALOG, enabled)
    }

    fun getThemeImageName(): String =
        getStringPreference(KEY_IMAGE_NAME, DEFAULT_THEME_IMAGE)

    fun setThemeImageName(fileName: String) {
        putStringPreference(KEY_IMAGE_NAME, fileName)
    }

    fun getThemeImageUris(): List<String> {
        val storedUris = getNullableStringPreference(KEY_THEME_SKIN_URIS)
            .orEmpty()
            .split(SKIN_URI_SEPARATOR)
            .map(String::trim)
            .filter(String::isNotEmpty)

        val selectedImage = getThemeImageName()
        val migrated = if (selectedImage.startsWith("/")) {
            storedUris + selectedImage
        } else {
            storedUris
        }

        return migrated.distinct()
    }

    fun setThemeImageUris(paths: List<String>) {
        val normalized = paths
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()

        putStringPreference(KEY_THEME_SKIN_URIS, normalized.joinToString(SKIN_URI_SEPARATOR))
    }

    fun addThemeImageUri(path: String) {
        val normalizedPath = path.trim()
        if (normalizedPath.isEmpty()) return
        setThemeImageUris(getThemeImageUris() + normalizedPath)
    }
}
