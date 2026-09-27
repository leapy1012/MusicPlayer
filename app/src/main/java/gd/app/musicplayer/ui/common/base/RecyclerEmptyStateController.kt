package gd.app.musicplayer.ui.common.base

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewStub
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.DrawableCompat
import androidx.recyclerview.widget.COUIRecyclerView
import androidx.recyclerview.widget.RecyclerView
import com.coui.appcompat.button.COUIButton
import dagger.hilt.android.EntryPointAccessors
import gd.app.lib.configuration.ConfigurationLinearLayout
import gd.app.musicplayer.R

import gd.app.musicplayer.core.common.extension.dpToPx
import gd.app.musicplayer.core.designsystem.theme.ThemeManager
import gd.app.musicplayer.core.designsystem.theme.ThemePalette
import gd.app.musicplayer.core.designsystem.theme.accentColor
import gd.app.musicplayer.core.designsystem.theme.titleColor
import gd.app.musicplayer.di.ThemeEntryPoint
import gd.app.musicplayer.ui.common.hostStaticContent

/**
 * @param onDarkSurface host is always dark regardless of app theme (e.g. lock screen), so the
 * empty state keeps light-on-dark styling even on the White theme.
 */
class RecyclerEmptyStateController(
    private val recyclerView: RecyclerView,
    private val emptyViewStub: ViewStub,
    private val onDarkSurface: Boolean = false
) : ConfigurationLinearLayout.OnSizeChangedListener {

    private var emptyRootView: View? = null
    private var actionButtonText: String? = null
    private var showActionButton: Boolean = false
    private var showExtraText: Boolean = false
    private var emptyMessage: String? = null
    private var extraText: String? = null
    private var emptyImageResId: Int = 0
    private var actionClickListener: View.OnClickListener? = null
    private var theme: ThemePalette? = null
    private var emptyContainer: ConfigurationLinearLayout? = null
    private var loadingProgressBar: ProgressBar? = null
    private var topInsetOffset: Int = 0

    private val showProgressRunnable = Runnable {
        loadingProgressBar?.visibility = View.VISIBLE
    }

    init {
        val parent = recyclerView.parent as? View
        loadingProgressBar = parent?.findViewById(R.id.loading_progress_bar)

        val progressColor = if (usesCouiStyling(resolveTheme())) {
            resolveAttrColor(com.coui.appcompat.R.attr.couiColorPrimary, Color.BLUE)
        } else {
            recyclerView.context.getColor(R.color.white)
        }
        loadingProgressBar?.indeterminateDrawable?.let { drawable ->
            DrawableCompat.setTintList(drawable, ColorStateList.valueOf(progressColor))
        }
    }

    fun showList() {
        if (emptyRootView != null) {
            recyclerView.post {
                emptyRootView?.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
            }
        }
    }

    /**
     * White theme: COUI defaults from `layout_list_empty.xml` (hint-gray art, secondary text,
     * HalfColor [COUIButton]). Picture/Night and dark hosts: palette-driven tint.
     */
    fun applyTheme(theme: ThemePalette) {
        this.theme = theme
        val root = emptyContainer ?: return
        val image = root.findViewById<ImageView>(R.id.empty_image)
        val texts = listOfNotNull(
            root.findViewById<TextView>(R.id.empty_text),
            root.findViewById<TextView>(R.id.empty_text_extra)
        )
        val button = root.findViewById<COUIButton>(R.id.empty_button)

        if (usesCouiStyling(theme)) {
            image?.imageTintList = ColorStateList.valueOf(
                resolveAttrColor(com.coui.appcompat.R.attr.couiColorHintNeutral, 0x4D000000)
            )
            val secondary = resolveAttrColor(
                com.coui.appcompat.R.attr.couiColorSecondNeutral,
                0x8C000000.toInt()
            )
            texts.forEach { it.setTextColor(secondary) }
            return
        }

        val usesDarkForeground = !onDarkSurface && theme.titleColor != Color.WHITE
        image?.imageTintList = ColorStateList.valueOf(
            (if (usesDarkForeground) 0x33000000 else 0x80FFFFFF).toInt()
        )
        val secondary = (if (usesDarkForeground) 0x8C000000 else 0x80FFFFFF).toInt()
        texts.forEach { it.setTextColor(secondary) }

        val accent = if (onDarkSurface) Color.WHITE else theme.accentColor
        button?.setTextColor(accent)
        button?.drawableColor = ColorUtils.setAlphaComponent(accent, PALETTE_BUTTON_FILL_ALPHA)
    }

    private fun usesCouiStyling(theme: ThemePalette): Boolean =
        !onDarkSurface && theme.getThemeType() == ThemeManager.THEME_TYPE_LIGHT

    private fun resolveTheme(): ThemePalette = theme ?: EntryPointAccessors
        .fromApplication(recyclerView.context.applicationContext, ThemeEntryPoint::class.java)
        .themeRepo
        .getCorePalette()

    private fun resolveAttrColor(attr: Int, fallback: Int): Int {
        val typed = recyclerView.context.obtainStyledAttributes(intArrayOf(attr))
        val color = typed.getColor(0, fallback)
        typed.recycle()
        return color
    }

    fun setActionClickListener(listener: View.OnClickListener) {
        actionClickListener = listener
    }

    fun setActionButtonText(text: String) {
        actionButtonText = text
    }

    fun setEmptyImage(imageResId: Int) {
        emptyImageResId = imageResId
        emptyContainer?.findViewById<ImageView>(R.id.empty_image)?.let { imageView ->
            if (imageResId != 0) {
                imageView.setImageResource(imageResId)
            } else {
                imageView.setImageDrawable(null)
            }
        }
    }

    fun setEmptyMessage(text: String) {
        emptyMessage = text
        emptyContainer?.findViewById<TextView>(R.id.empty_text)?.text = text
    }

    fun setExtraText(text: String) {
        extraText = text
        emptyContainer?.findViewById<TextView>(R.id.empty_text_extra)?.text = text
    }

    fun setVisible(showEmpty: Boolean) {
        if (showEmpty) showEmpty() else showList()
    }

    fun setLoadingEnabled(enabled: Boolean) {
        val progressBar = loadingProgressBar ?: return
        progressBar.removeCallbacks(showProgressRunnable)

        if (enabled) {
            progressBar.postDelayed(showProgressRunnable, 1000L)
        } else {
            progressBar.visibility = View.GONE
            loadingProgressBar = null
        }
    }

    fun setActionButtonVisible(visible: Boolean) {
        showActionButton = visible
    }

    fun setExtraTextVisible(visible: Boolean) {
        showExtraText = visible
    }

    fun showEmpty() {
        if (emptyRootView == null) {
            val inflated = emptyViewStub.inflate()
            emptyRootView = inflated

            val container = inflated.findViewById<ConfigurationLinearLayout>(R.id.empty_linear_layout)
            emptyContainer = container
            inflated.findViewById<COUIRecyclerView>(R.id.empty_spring_host)
                ?.hostStaticContent(container)
            container.visibility = View.INVISIBLE
            container.setOnViewSizeChangeListener(this)

            val actionButton = container.findViewById<TextView>(R.id.empty_button)
            if (showActionButton) {
                actionButtonText?.let(actionButton::setText)
                actionButton.visibility = View.VISIBLE
                actionClickListener?.let(actionButton::setOnClickListener)
            } else {
                actionButton.visibility = View.GONE
            }

            extraText?.let {
                container.findViewById<TextView>(R.id.empty_text_extra).text = it
            }

            container.findViewById<View>(R.id.empty_text_extra).visibility =
                if (showExtraText) View.VISIBLE else View.GONE

            emptyMessage?.let {
                container.findViewById<TextView>(R.id.empty_text).text = it
            }

            if (emptyImageResId != 0) {
                container.findViewById<ImageView>(R.id.empty_image).setImageResource(emptyImageResId)
            }

            // The stub inflates after the screen's theme pass, so theme it here.
            applyTheme(resolveTheme())
        }

        recyclerView.post {
            emptyRootView?.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        }
    }

    private fun repositionEmptyContent() {
        val container = emptyContainer ?: return
        val parent = container.parent as? View ?: return

        val parentHeight = parent.height
        val contentHeight = container.height

        val reservedTopSpace = container.context.dpToPx(80f) + topInsetOffset
        val topMargin = maxOf(0, ((parentHeight - reservedTopSpace) - contentHeight) / 2)

        if (parentHeight <= 0 || reservedTopSpace <= 0) return

        val params = container.layoutParams as ViewGroup.MarginLayoutParams
        params.topMargin = topMargin
        if (params is FrameLayout.LayoutParams) {
            params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        }
        container.layoutParams = params
        container.visibility = View.VISIBLE
    }

    override fun onSizeChanged(
        view: LinearLayout,
        width: Int,
        height: Int
    ) {
        if (width <= 0 || height <= 0) return

        view.post {
            repositionEmptyContent()
        }
    }

    private companion object {
        const val PALETTE_BUTTON_FILL_ALPHA = 0x33
    }
}
