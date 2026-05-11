package gd.app.musicplayer.core.designsystem.theme

import android.view.View

fun interface ThemeViewBinder {
    fun bind(palette: ThemePalette, payload: Any?, view: View): Boolean
}
