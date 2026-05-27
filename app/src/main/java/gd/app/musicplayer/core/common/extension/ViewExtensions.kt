package gd.app.musicplayer.core.common.extension

import android.graphics.Color
import android.view.View
import android.view.ViewGroup
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
import gd.app.lib.view.RoundedOutlineProvider
import jp.wasabeef.glide.transformations.BlurTransformation


fun View.applyStatusBarInsetHeight() {
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top

        view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            height = statusBarHeight
        }

        insets
    }

    ViewCompat.requestApplyInsets(this)
}

fun View.applySystemBarInsets(
    statusBarView: View? = null,
    bottomPaddingView: View? = null,
) {
    ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
        val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

        statusBarView?.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            height = systemBars.top
        }

        bottomPaddingView?.updatePadding(bottom = systemBars.bottom)

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