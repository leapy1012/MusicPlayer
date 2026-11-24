package gd.app.musicplayer.core.designsystem.theme

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.ImageView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.recyclerview.widget.RecyclerView
import com.coui.appcompat.R as CouiR
import com.coui.appcompat.button.COUIButton
import com.coui.appcompat.checkbox.COUICheckBox
import com.coui.appcompat.couiswitch.COUISwitch
import com.coui.appcompat.edittext.COUIEditText
import com.coui.appcompat.searchview.COUISearchBar
import com.coui.appcompat.tablayout.COUITabLayout
import com.coui.appcompat.seekbar.COUISeekBar
import gd.app.musicplayer.R
import java.util.Collections
import java.util.IdentityHashMap

/** Refreshes existing controls; view identity, input, focus and scroll state stay intact. */
class LiveThemeUpdater(private val bind: (View) -> Unit) {
    private val historicalColors = mutableMapOf<Int, Int>()
    private val historicalBackgrounds = mutableMapOf<Int, Int>()
    private val contexts = Collections.newSetFromMap(IdentityHashMap<Context, Boolean>())

    fun rememberColors(theme: Resources.Theme, previousAccent: Int? = null) {
        val values = theme.obtainStyledAttributes(COLOR_ATTRIBUTES)
        try {
            COLOR_ATTRIBUTES.forEachIndexed { index, attr ->
                values.getColorStateList(index)?.let { colors ->
                    historicalColors[colors.defaultColor] = attr
                    STATES.forEach { historicalColors[colors.getColorForState(it, colors.defaultColor)] = attr }
                }
            }
        } finally { values.recycle() }
        previousAccent?.let { historicalColors[it] = CouiR.attr.couiColorPrimary }
        val surfaces = theme.obtainStyledAttributes(SURFACE_ATTRIBUTES)
        try {
            SURFACE_ATTRIBUTES.forEachIndexed { index, attr ->
                historicalBackgrounds[surfaces.getColor(index, 0)] = attr
            }
        } finally { surfaces.recycle() }
        contexts.clear()
    }

    fun refresh(root: View) {
        refreshView(root)
        bind(root)
    }

    // AlertDialog and themed inflater contexts cache a copy of their parent's Theme.
    fun refreshContext(context: Context) {
        if (context is android.app.Activity) return
        if (!contexts.add(context)) return
        if (context is ContextThemeWrapper) {
            refreshContext(context.baseContext)
            val style = context.themeResId
            context.theme.setTo(context.baseContext.theme)
            if (style != 0) context.theme.applyStyle(style, true)
        }
    }

    private fun refreshView(view: View) {
        refreshContext(view.context)
        when (view) {
            is com.coui.appcompat.chip.COUIChip -> view.chipDrawable.refresh(view.context)
            is COUIButton -> view.refresh()
            is COUICheckBox -> view.refresh()
            is COUISwitch -> view.refresh()
            is COUIEditText -> view.refresh()
            is COUISearchBar -> view.refresh()
            is COUITabLayout -> view.refresh()
            // Toolbar.refresh() replaces navigation icons and reinflates its menu.
            // ThemeRegistry's toolbar binder recolors the existing controls instead.
            is COUISeekBar -> {
                val colors = view.context.obtainStyledAttributes(null, CouiR.styleable.COUISeekBar, CouiR.attr.couiSeekBarStyle, 0)
                try {
                    colors.getColorStateList(CouiR.styleable.COUISeekBar_couiSeekBarBackgroundColor)?.let(view::setSeekBarBackgroundColor)
                    colors.getColorStateList(CouiR.styleable.COUISeekBar_couiSeekBarProgressColor)?.let(view::setProgressColor)
                    colors.getColorStateList(CouiR.styleable.COUISeekBar_couiSeekBarThumbColor)?.let(view::setThumbColor)
                } finally { colors.recycle() }
            }
        }
        if (view is TextView && view !is COUIButton) {
            view.setTextColor(remap(view, view.textColors, R.id.live_theme_text_attribute))
            view.setHintTextColor(remap(view, view.hintTextColors, R.id.live_theme_hint_attribute))
        }
        if (view is ImageView) view.imageTintList?.let { view.imageTintList = remap(view, it, R.id.live_theme_icon_attribute) }
        (view.background as? ColorDrawable)?.let { background ->
            historicalBackgrounds[background.color]?.let { attr ->
                resolve(view.context, attr)?.let { background.mutate(); background.color = it.defaultColor }
            }
        }
        view.background?.let { if (it.canApplyTheme()) it.applyTheme(view.context.theme) }
        view.refreshDrawableState()
        view.invalidate()
        if (view is RecyclerView && view.getTag(R.id.live_theme_recycler_binding) == null) {
            view.setTag(R.id.live_theme_recycler_binding, true)
            view.addOnChildAttachStateChangeListener(object : RecyclerView.OnChildAttachStateChangeListener {
                override fun onChildViewAttachedToWindow(child: View) { refresh(child) }
                override fun onChildViewDetachedFromWindow(child: View) = Unit
            })
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) refreshView(view.getChildAt(index))
        }
    }

    private fun remap(view: View, colors: ColorStateList, key: Int): ColorStateList {
        val attr = view.getTag(key) as? Int ?: historicalColors[colors.defaultColor] ?: return colors
        view.setTag(key, attr)
        return resolve(view.context, attr) ?: colors
    }

    private fun resolve(context: Context, attr: Int): ColorStateList? {
        val values = context.obtainStyledAttributes(intArrayOf(attr))
        return try { values.getColorStateList(0) } finally { values.recycle() }
    }

    companion object {
        private val STATES = arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf(android.R.attr.state_checked))
        private val COLOR_ATTRIBUTES = intArrayOf(
            android.R.attr.textColorPrimary, android.R.attr.textColorSecondary, android.R.attr.textColorHint,
            CouiR.attr.couiColorDivider,
            CouiR.attr.couiColorLabelPrimary, CouiR.attr.couiColorLabelSecondary,
            CouiR.attr.couiColorPrimaryNeutral, CouiR.attr.couiColorSecondNeutral,
            CouiR.attr.couiColorHintNeutral, CouiR.attr.couiColorPrimary,
            CouiR.attr.couiColorError,
        )
        private val SURFACE_ATTRIBUTES = intArrayOf(
            CouiR.attr.couiColorBackgroundWithCard, CouiR.attr.couiColorCardBackground, CouiR.attr.couiColorSurface,
        )
    }
}
