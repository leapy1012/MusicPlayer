package gd.app.musicplayer.core.designsystem.dialog

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.InsetDrawable
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.drawable.toDrawable
import dagger.hilt.android.EntryPointAccessors
import gd.app.musicplayer.di.ThemeEntryPoint
import gd.app.musicplayer.core.designsystem.theme.ThemeManager
import gd.app.musicplayer.ui.common.base.BaseActivity

/**
 * Keeps COUI's native surface, clipping and day/night colors for solid themes.
 * Only picture themes replace the surface with their blurred wallpaper.
 */
object CouiAlertDialogSurface {

    /** Re-resolve native chrome after an existing dialog's Context theme changes. */
    fun refresh(dialog: AlertDialog) {
        val panel = dialog.findViewById<View>(com.coui.appcompat.R.id.parentPanel)
        panel?.setBackgroundResource(com.coui.appcompat.R.drawable.coui_alert_dialog_builder_background)
        apply(dialog)
    }

    @JvmStatic
    @JvmOverloads
    fun apply(dialog: AlertDialog, surface: Drawable? = null) {
        val palette = resolvePalette(dialog) ?: return
        if (palette.getThemeType() != ThemeManager.THEME_TYPE_PICTURE) return
        val window = dialog.window ?: return
        window.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())

        val parentPanel = dialog.findViewById<View>(com.coui.appcompat.R.id.parentPanel)
        val rootView = dialog.findViewById<View>(com.coui.appcompat.R.id.rootView)
        val target = parentPanel ?: rootView ?: return

        val resolved = (surface ?: palette.getDialogSurfaceDrawable(dialog.context)).mutate()
        target.background = if (parentPanel != null) {
            insetLikeCouiBuilder(parentPanel.context, resolved)
        } else {
            resolved
        }
    }

    private fun resolvePalette(dialog: AlertDialog): gd.app.musicplayer.core.designsystem.theme.ThemePalette? {
        val activity = findActivity(dialog.ownerActivity) ?: findActivity(dialog.context)
        return (activity as? BaseActivity)?.themeEngine?.currentTheme()
            ?: resolvePaletteFromApplication(dialog.context)
    }

    private fun resolvePaletteFromApplication(context: Context) = try {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            ThemeEntryPoint::class.java
        ).themeRepo.getCorePalette()
    } catch (_: Exception) {
        null
    }

    private fun findActivity(context: Context?): Activity? {
        var current = context
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return current as? Activity
    }

    /** Match [coui_alert_dialog_builder_background] insets so width/margins stay COUI-correct. */
    private fun insetLikeCouiBuilder(context: Context, surface: Drawable): Drawable {
        val horizontal = context.resources.getDimensionPixelSize(
            com.coui.appcompat.R.dimen.coui_dialog_layout_margin_horizontal
        )
        val vertical = context.resources.getDimensionPixelSize(
            com.coui.appcompat.R.dimen.coui_dialog_layout_margin_vertical
        )
        return InsetDrawable(surface, horizontal, 0, horizontal, vertical)
    }
}
