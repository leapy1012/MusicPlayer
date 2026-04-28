package gd.app.lib.view.translucent

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.util.AttributeSet
import android.view.WindowInsets
import androidx.core.content.withStyledAttributes
import gd.app.lib.R
import gd.app.lib.configuration.ConfigurationFrameLayout

interface NavigationBarColorHost  {
    fun setNavigationBarColor(color: Int)
}

interface WindowInsetsListenerHost {
    fun addWindowInsetsListener(listener: OnWindowInsetsChangedListener)
}

fun interface OnWindowInsetsChangedListener {
    fun onWindowInsetsChanged(windowInsets: WindowInsets)
}

class TranslucentBarFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConfigurationFrameLayout(context, attrs, defStyleAttr), WindowInsetsListenerHost, NavigationBarColorHost {

    private val windowInsetsHelper = WindowInsetsHelper()

    init {
        if (attrs != null) {
            context.withStyledAttributes(attrs, R.styleable.TranslucentBarFrameLayout, defStyleAttr, 0) {

                windowInsetsHelper.allowStatusBarPadding =
                    getBoolean(R.styleable.TranslucentBarFrameLayout_allowStatusBarPadding, false)
                windowInsetsHelper.allowNavigationBarPaddingLeft =
                    getBoolean(R.styleable.TranslucentBarFrameLayout_allowNavigationBarPaddingLeft, true)
                windowInsetsHelper.allowNavigationBarPaddingRight =
                    getBoolean(R.styleable.TranslucentBarFrameLayout_allowNavigationBarPaddingRight, true)
                windowInsetsHelper.allowNavigationBarPadding =
                    getBoolean(R.styleable.TranslucentBarFrameLayout_allowNavigationBarPadding, true)
                windowInsetsHelper.allowNavigationBarPaddingBottom =
                    getBoolean(R.styleable.TranslucentBarFrameLayout_allowNavigationBarPaddingBottom, true)
                windowInsetsHelper.statusBarColor =
                    getColor(R.styleable.TranslucentBarFrameLayout_customStatusBarColor, Color.TRANSPARENT)
                windowInsetsHelper.navigationBarColor =
                    getColor(R.styleable.TranslucentBarFrameLayout_customStatusBarColor, Color.TRANSPARENT)
                windowInsetsHelper.allowLandscapeDisplayCutout =
                    getBoolean(R.styleable.TranslucentBarFrameLayout_allowLandscapeDisplayCutout, false)

            }
        }
    }

    override fun addWindowInsetsListener(listener: OnWindowInsetsChangedListener) {
        windowInsetsHelper.addInsetsListener(listener)
    }

    override fun computeSystemWindowInsets(insets: WindowInsets, outLocalInsets: Rect): WindowInsets {
        val computedInsets = super.computeSystemWindowInsets(insets, outLocalInsets)
        windowInsetsHelper.adjustInsets(outLocalInsets, this.layoutDirection == 1)
        return computedInsets
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        windowInsetsHelper.drawSystemBarOverlays(
            canvas,
            width,
            height,
            paddingLeft,
            paddingTop,
            paddingRight,
            paddingBottom
        )
    }

    override fun fitSystemWindows(insets: Rect): Boolean {
        windowInsetsHelper.adjustInsets (insets, this.layoutDirection == 1)
        return super.fitSystemWindows(insets)
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        windowInsetsHelper.dispatchWindowInsets(insets)
        return super.onApplyWindowInsets(insets)
    }

    fun setAllowStatusBarPadding(enabled: Boolean) {
        windowInsetsHelper.allowStatusBarPadding = enabled
    }

    fun setAllowNavigationBarPadding(enabled: Boolean) {
        windowInsetsHelper.allowNavigationBarPadding = enabled
    }

    fun setAllowNavigationBarPaddingLeft(enabled: Boolean) {
        windowInsetsHelper.allowNavigationBarPaddingLeft = enabled
    }

    fun setAllowNavigationBarPaddingRight(enabled: Boolean) {
        windowInsetsHelper.allowNavigationBarPaddingRight = enabled
    }

    fun setAllowNavigationBarPaddingBottom(enabled: Boolean) {
        windowInsetsHelper.allowNavigationBarPaddingBottom = enabled
    }

    fun setAllowLandscapeDisplayCutout(enabled: Boolean) {
        windowInsetsHelper.allowLandscapeDisplayCutout = enabled
    }

    override fun setNavigationBarColor(color: Int) {
        if (windowInsetsHelper.navigationBarColor != color) {
            windowInsetsHelper.navigationBarColor = color
            postInvalidate()
        }
    }

    fun setStatusBarColor(color: Int) {
        if (windowInsetsHelper.statusBarColor != color) {
            windowInsetsHelper.statusBarColor = color
            postInvalidate()
        }
    }

    override fun setOnApplyWindowInsetsListener(listener: OnApplyWindowInsetsListener?) {
        // Intentionally ignored to preserve the original behavior.
    }
}