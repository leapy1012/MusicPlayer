package gd.app.musicplayer.util

import android.graphics.Color

object ColorBrightnessUtils {

    fun isDarkColor(color: Int): Boolean =
        Color.red(color) <= 77 &&
                Color.green(color) <= 77 &&
                Color.blue(color) <= 77

    fun isLightColor(color: Int): Boolean =
        Color.red(color) >= 179 &&
                Color.green(color) >= 179 &&
                Color.blue(color) >= 179
}
