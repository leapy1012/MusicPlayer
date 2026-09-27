package gd.app.musicplayer.core.common.extension

import android.content.Context
import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.coui.appcompat.poplist.DefaultAdapter
import com.coui.appcompat.state.COUIMaskEffectDrawable
import gd.app.lib.view.RoundedOutlineProvider
import jp.wasabeef.glide.transformations.BlurTransformation


fun View.applyStatusBarInsetHeight() {
    // Prefer sync cached height (original w0.h / o0.s) so open does not remasure
    // mid music_activity_in when insets arrive asynchronously.
    if (applyCachedStatusBarHeight()) return

    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top

        view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            height = statusBarHeight
        }

        insets
    }

    ViewCompat.requestApplyInsets(this)
}

/**
 * Original [o8.w0.h]: set status_bar_space height from cached dimen once.
 * Avoids AndroidX inset listener → updateLayoutParams → full remasure during enter fade.
 *
 * @return true if a non-zero height was applied from resources / window metrics
 */
fun View.applyCachedStatusBarHeight(): Boolean {
    val height = context.resolveStatusBarHeightPx()
    if (height <= 0) return false
    updateLayoutParams<ViewGroup.MarginLayoutParams> {
        this.height = height
    }
    return true
}

fun Context.resolveStatusBarHeightPx(): Int {
    val resId = resources.getIdentifier("status_bar_height", "dimen", "android")
    if (resId > 0) {
        return resources.getDimensionPixelSize(resId)
    }
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val windowManager = getSystemService(WindowManager::class.java) ?: return 0
        windowManager.currentWindowMetrics.windowInsets
            .getInsets(WindowInsets.Type.statusBars())
            .top
    } else {
        0
    }
}

fun View.applySystemBarInsets(
    statusBarView: View? = null,
    bottomPaddingView: View? = null,
) {
    // Match original Settings/Theme open: status bar spacer from cache; do not wait for
    // async insets to remasure the whole tree during music_activity_in.
    statusBarView?.applyCachedStatusBarHeight()

    if (bottomPaddingView == null) return

    ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
        val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

        // Only update status spacer if cache missed (foldables / unusual cutouts).
        statusBarView?.let { spacer ->
            val top = systemBars.top
            if (top > 0 && spacer.layoutParams.height != top) {
                spacer.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    height = top
                }
            }
        }

        bottomPaddingView.updatePadding(bottom = systemBars.bottom)

        insets
    }

    ViewCompat.requestApplyInsets(this)
}

fun View.applyRoundedOutline(radiusRes: Int) {
    val radius = resources.getDimension(radiusRes)

    post {
        outlineProvider = RoundedOutlineProvider(radius)
        clipToOutline = true
        invalidateOutline()
    }
}

/**
 * Installs COUI list/container press feedback ([COUIMaskEffectDrawable] + touch enter/exit).
 * Safe to call repeatedly; skips if a mask is already installed.
 */
fun View.installCouiPressFeedback(
    maskType: Int = COUIMaskEffectDrawable.MASK_EFFECT_TYPE_CONTAINER_WIDGET,
    roundStyle: Boolean = false
) {
    isClickable = true
    if (background is COUIMaskEffectDrawable) return

    val mask = COUIMaskEffectDrawable(context, maskType).apply {
        enableFocusedState(false)
        setIsRoundStyle(roundStyle)
        setTouchExited()
    }
    background = mask
    setOnTouchListener { view, event ->
        DefaultAdapter.onStateEffectTouchEvent(view, event)
    }
}

fun Toolbar.navigateBack(fragment: Fragment) {
    setNavigationOnClickListener {
        fragment.requireActivity().onBackPressedDispatcher.onBackPressed()
    }
}

fun View.navigateBack(activity: ComponentActivity) {
    setOnClickListener {
        activity.onBackPressedDispatcher.onBackPressed()
    }
}

internal fun View.updateWidth(width: Int) {
    updateLayoutParams {
        this.width = width
    }
}