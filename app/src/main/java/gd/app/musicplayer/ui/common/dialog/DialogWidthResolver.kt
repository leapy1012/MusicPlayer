package gd.app.musicplayer.ui.common.dialog

import android.content.Context
import android.content.res.Configuration
import android.view.ViewGroup
import gd.app.musicplayer.core.util.ScreenUtils

internal object DialogWidthResolver {

    fun resolve(
        context: Context,
        widthPx: Int,
        configuration: Configuration? = null
    ): Int {
        return when (widthPx) {
            BaseDialog.Config.WIDTH_90_PERCENT -> {
                if (configuration != null) {
                    ScreenUtils.getScaledSizeByConfiguration(context, configuration, 0.9f)
                } else {
                    ScreenUtils.getScaledSizeByCurrentOrientation(context, 0.9f)
                }
            }

            else -> widthPx
        }
    }

    fun isMatchParent(widthPx: Int): Boolean {
        return widthPx == ViewGroup.LayoutParams.MATCH_PARENT
    }

    fun isWrapContent(widthPx: Int): Boolean {
        return widthPx == ViewGroup.LayoutParams.WRAP_CONTENT
    }
}