package gd.app.musicplayer.data.local.preference

data class DesktopLyricPreference(
    val visible: Boolean = false,
    val locked: Boolean = false,
    val pendingEnableAfterPermission: Boolean = false
)