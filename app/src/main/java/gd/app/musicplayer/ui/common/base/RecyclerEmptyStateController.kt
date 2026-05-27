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
import androidx.core.graphics.drawable.DrawableCompat
import androidx.recyclerview.widget.RecyclerView
import gd.app.lib.configuration.ConfigurationLinearLayout
import gd.app.musicplayer.R

import gd.app.musicplayer.core.common.extension.dpToPx
import gd.app.musicplayer.core.designsystem.drawable.outlinedRoundedRippleDrawable
import gd.app.musicplayer.core.designsystem.theme.ThemePalette
import gd.app.musicplayer.core.designsystem.theme.accentColor
import gd.app.musicplayer.core.designsystem.theme.rippleColor
import gd.app.musicplayer.core.designsystem.theme.titleColor

class RecyclerEmptyStateController(
    private val recyclerView: RecyclerView,
    private val emptyViewStub: ViewStub
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

        loadingProgressBar?.indeterminateDrawable?.let { drawable ->
            DrawableCompat.setTintList(drawable, ColorStateList.valueOf(recyclerView.context.getColor(R.color.white)))
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

    fun applyTheme(theme: ThemePalette) {
        this.theme = theme
        val root = emptyRootView ?: return
        val button = root.findViewById<TextView>(R.id.empty_button)
        val usesDarkForegroundPalette = theme.titleColor != Color.WHITE
        button.setTextColor(theme.accentColor)
        tintCompoundDrawables(button, theme.accentColor)
        button.background = outlinedRoundedRippleDrawable(
            cornerRadius = button.context.dpToPx(100f),
            strokeWidth = button.context.dpToPx(1f),
            strokeColor = if (usesDarkForegroundPalette) 0x1A000000 else 0x33FFFFFF,
            rippleColor = theme.rippleColor
        )

        root.findViewById<ImageView>(R.id.empty_image)?.imageTintList =
            ColorStateList.valueOf((if (usesDarkForegroundPalette) 0x33000000 else 0x80FFFFFF).toInt())
    }

    fun setActionClickListener(listener: View.OnClickListener) {
        actionClickListener = listener
    }

    fun setActionButtonText(text: String) {
        actionButtonText = text
    }

    fun setEmptyImage(imageResId: Int) {
        emptyImageResId = imageResId
        emptyRootView?.findViewById<ImageView>(R.id.empty_image)?.let { imageView ->
            if (imageResId != 0) {
                imageView.setImageResource(imageResId)
            } else {
                imageView.setImageDrawable(null)
            }
        }
    }

    fun setEmptyMessage(text: String) {
        emptyMessage = text
        emptyRootView?.findViewById<TextView>(R.id.empty_text)?.text = text
    }

    fun setExtraText(text: String) {
        extraText = text
        emptyRootView?.findViewById<TextView>(R.id.empty_text_extra)?.text = text
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
            container.visibility = View.INVISIBLE
            container.setOnViewSizeChangeListener(this)

            val actionButton = inflated.findViewById<TextView>(R.id.empty_button)
            if (showActionButton) {
                actionButtonText?.let(actionButton::setText)
                actionButton.visibility = View.VISIBLE
                actionClickListener?.let(actionButton::setOnClickListener)
            } else {
                actionButton.visibility = View.GONE
            }

            extraText?.let {
                inflated.findViewById<TextView>(R.id.empty_text_extra).text = it
            }

            inflated.findViewById<View>(R.id.empty_text_extra).visibility =
                if (showExtraText) View.VISIBLE else View.GONE

            emptyMessage?.let {
                inflated.findViewById<TextView>(R.id.empty_text).text = it
            }

            if (emptyImageResId != 0) {
                inflated.findViewById<ImageView>(R.id.empty_image).setImageResource(emptyImageResId)
            }

            theme?.let(::applyTheme)
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

    private fun tintCompoundDrawables(textView: TextView, color: Int) {
        textView.compoundDrawables.filterNotNull().forEach { drawable ->
            DrawableCompat.setTint(drawable.mutate(), color)
        }
        textView.compoundDrawablesRelative.filterNotNull().forEach { drawable ->
            DrawableCompat.setTint(drawable.mutate(), color)
        }
    }
}
