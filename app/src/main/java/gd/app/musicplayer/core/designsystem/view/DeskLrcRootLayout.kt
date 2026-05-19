package gd.app.musicplayer.core.designsystem.view

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout

class DeskLrcRootLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private var actionListener: OnActionListener? = null
    private var touchDownTimeMs: Long = 0L

    interface OnActionListener {
        /**
         * Called when the user starts touching this layout.
         */
        fun onTouchStart(view: View)

        /**
         * Called when the user releases or cancels the touch.
         */
        fun onTouchEnd(view: View)

        /**
         * Called when the user quickly taps outside the vertical bounds of this layout.
         */
        fun onQuickOutsideTap(view: View)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        val listener = actionListener ?: return super.dispatchTouchEvent(event)

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchDownTimeMs = System.currentTimeMillis()
                listener.onTouchStart(this)
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                listener.onTouchEnd(this)

                val isOutsideVerticalBounds = event.y < 0f || event.y > height
                val isQuickTap = System.currentTimeMillis() - touchDownTimeMs < QUICK_TAP_THRESHOLD_MS

                if (isOutsideVerticalBounds && isQuickTap) {
                    listener.onQuickOutsideTap(this)
                }
            }
        }

        return super.dispatchTouchEvent(event)
    }

    fun setOnActionListener(listener: OnActionListener?) {
        actionListener = listener
    }

    companion object {
        private const val QUICK_TAP_THRESHOLD_MS = 230L
    }
}