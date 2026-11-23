package gd.app.musicplayer.core.designsystem.theme

import android.content.Context
import androidx.appcompat.view.ContextThemeWrapper
import com.coui.appcompat.contextutil.COUIContextUtil
import com.coui.appcompat.theme.COUIThemeOverlay
import com.coui.appcompat.R as CouiR

/** Read the exact OEM role instead of approximating the packed Colors setting. */
object SystemDefaultAccent {
    fun resolve(context: Context): Int? = try {
        val overlay = COUIThemeOverlay.getInstance()
        if (overlay.getCOUITheme(context.resources.configuration) == 0L || overlay.isRejectTheme(context)) null
        else ContextThemeWrapper(context, CouiR.style.Theme_COUI_Main).let {
            overlay.applyThemeOverlays(it)
            COUIContextUtil.getAttrColor(it, CouiR.attr.couiColorPrimary)
        }
    } catch (_: LinkageError) { null } catch (_: RuntimeException) { null }

    fun choose(explicit: Boolean, appAccent: Int, systemAccent: Int?): Int =
        if (explicit) appAccent else systemAccent ?: appAccent
}
