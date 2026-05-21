package gd.app.musicplayer.data.local.preference

data class DesktopLyricPreference(
    val visible: Boolean = false,
    val locked: Boolean = false,
    val pendingEnableAfterPermission: Boolean = false,
    val presetColorIndex: Int = 0,
    val currentColorProgress: Int = 0,
    val normalColorProgress: Int = 0,
    val alpha: Float = 1f,
    val textSize: Int = 16,
    val y: Int = -1
)
