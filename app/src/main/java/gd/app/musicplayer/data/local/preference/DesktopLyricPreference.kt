package gd.app.musicplayer.data.local.preference

data class DesktopLyricPreference(
    val visible: Boolean = false,
    val locked: Boolean = false,
    val pendingEnableAfterPermission: Boolean = false,
    val presetColorIndex: Int = 0,
    val currentColorProgress: Int = 28,
    val normalColorProgress: Int = 19,
    val alpha: Float = 1f,
    val textSize: Int = 18,
    val y: Int = -1
)
