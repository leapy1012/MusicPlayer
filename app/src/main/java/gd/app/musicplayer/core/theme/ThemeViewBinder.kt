package gd.app.musicplayer.core.theme

import android.view.View

fun interface ThemeViewBinder {
    fun bind(palette: ThemePalette, payload: Any?, view: View): Boolean
}
