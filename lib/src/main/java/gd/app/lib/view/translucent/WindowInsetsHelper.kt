package gd.app.lib.view.translucent

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.view.WindowInsets

class WindowInsetsHelper {

    private var overlayPaint: Paint? = null

    var allowStatusBarPadding: Boolean = false
    var allowNavigationBarPadding: Boolean = true
    var allowNavigationBarPaddingLeft: Boolean = true
    var allowNavigationBarPaddingRight: Boolean = true
    var allowNavigationBarPaddingBottom: Boolean = true
    var allowLandscapeDisplayCutout: Boolean = false

    var navigationBarColor: Int = 0
    var statusBarColor: Int = 0

    private val displayCutoutInsets = Rect()
    private val insetsListeners = mutableListOf<OnWindowInsetsChangedListener>()

    fun addInsetsListener(listener: OnWindowInsetsChangedListener?) {
        if (listener == null || insetsListeners.contains(listener)) return
        insetsListeners.add(listener)
    }

    fun drawSystemBarOverlays(
        canvas: Canvas,
        width: Int,
        height: Int,
        paddingLeft: Int,
        paddingTop: Int,
        paddingRight: Int,
        paddingBottom: Int
    ) {
        if (navigationBarColor != 0) {
            val paint = getOrCreatePaint().apply { color = navigationBarColor }

            when {
                paddingBottom > 0 -> {
                    canvas.drawRect(
                        0f,
                        (height - paddingBottom).toFloat(),
                        width.toFloat(),
                        height.toFloat(),
                        paint
                    )
                }

                paddingRight > 0 -> {
                    canvas.drawRect(
                        (width - paddingRight).toFloat(),
                        0f,
                        width.toFloat(),
                        height.toFloat(),
                        paint
                    )
                }

                paddingLeft > 0 -> {
                    canvas.drawRect(
                        0f,
                        0f,
                        paddingLeft.toFloat(),
                        height.toFloat(),
                        paint
                    )
                }
            }
        }

        if (statusBarColor != 0 && paddingTop > 0) {
            val paint = getOrCreatePaint().apply { color = statusBarColor }
            canvas.drawRect(
                0f,
                0f,
                width.toFloat(),
                paddingTop.toFloat(),
                paint
            )
        }
    }

    fun adjustInsets(insets: Rect, isRtl: Boolean) {
        if (!allowStatusBarPadding) {
            insets.top = 0
        }

        if (!allowNavigationBarPadding || !allowNavigationBarPaddingLeft) {
            if (isRtl) {
                insets.right = 0
            } else {
                insets.left = 0
            }
        }

        if (!allowNavigationBarPadding || !allowNavigationBarPaddingRight) {
            if (isRtl) {
                insets.left = 0
            } else {
                insets.right = 0
            }
        }

        if (!allowNavigationBarPadding || !allowNavigationBarPaddingBottom) {
            insets.bottom = 0
        }

        if (allowLandscapeDisplayCutout) return


        Log.e(
            "WindowInsetsHelper",
            "interceptInsets displayCutoutInsets:$displayCutoutInsets insets:$insets"
        )

        when {
            displayCutoutInsets.left == insets.left -> insets.left = 0
            displayCutoutInsets.right == insets.right -> insets.right = 0
        }
    }

    fun dispatchWindowInsets(windowInsets: WindowInsets) {
        if (Build.VERSION.SDK_INT >= 30) {
            val cutoutInsets = windowInsets.getInsets(WindowInsets.Type.displayCutout())
            displayCutoutInsets.set(
                cutoutInsets.left,
                cutoutInsets.top,
                cutoutInsets.right,
                cutoutInsets.bottom
            )
        }

        insetsListeners.forEach { listener ->
            listener.onWindowInsetsChanged(windowInsets)
        }
    }

    private fun getOrCreatePaint(): Paint {
        return overlayPaint ?: Paint(Paint.ANTI_ALIAS_FLAG).also {
            overlayPaint = it
        }
    }
}