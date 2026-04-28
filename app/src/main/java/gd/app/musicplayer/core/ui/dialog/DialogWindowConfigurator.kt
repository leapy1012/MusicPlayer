package gd.app.musicplayer.core.ui.dialog

import android.graphics.Color
import android.os.Build
import android.view.Window
import android.view.WindowManager
import androidx.core.graphics.drawable.toDrawable

internal object DialogWindowConfigurator {

    fun configureBeforeContent(window: Window, config: BaseDialog.Config) {
        requestFeatures(window, config)
        configureWindowAttributes(window, config)
    }

    private fun requestFeatures(window: Window, config: BaseDialog.Config) {
        if (config.requestNoTitleFeature) {
            window.requestFeature(Window.FEATURE_NO_TITLE)
        }
    }

    private fun configureWindowAttributes(window: Window, config: BaseDialog.Config) {
        val params = window.attributes ?: return

        params.width = DialogWidthResolver.resolve(
            context = window.context,
            widthPx = config.widthPx
        )
        params.height = config.heightPx

        params.flags = params.flags or WindowManager.LayoutParams.FLAG_DIM_BEHIND
        params.dimAmount = config.dimAmount

        if (config.gravity != 0) {
            params.gravity = config.gravity
        }

        if (config.softInputMode != -1) {
            params.softInputMode = config.softInputMode
        }

        if (Build.VERSION.SDK_INT >= 28 && config.layoutInDisplayCutoutMode != -1) {
            params.layoutInDisplayCutoutMode = config.layoutInDisplayCutoutMode
        }

        window.attributes = params
        window.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())

        if (config.windowAnimationRes != -1) {
            window.setWindowAnimations(config.windowAnimationRes)
        }
    }
}