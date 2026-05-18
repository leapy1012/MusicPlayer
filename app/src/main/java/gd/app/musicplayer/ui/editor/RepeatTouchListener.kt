package gd.app.musicplayer.ui.editor

import android.view.MotionEvent
import android.view.View

class RepeatTouchListener(
    private val repeatDelayMs: Long = DEFAULT_REPEAT_DELAY_MS,
    private val initialDelayMs: Long = DEFAULT_INITIAL_DELAY_MS,
    private val onRepeat: (View) -> Unit,
    private val onClick: ((View) -> Unit)? = null
) : View.OnTouchListener, Runnable {

    private var targetView: View? = null
    private var repeated = false

    override fun onTouch(
        view: View,
        event: MotionEvent
    ): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                view.isPressed = true
                targetView = view
                repeated = false
                view.postDelayed(this, initialDelayMs)
                return true
            }

            MotionEvent.ACTION_UP -> {
                view.isPressed = false
                view.removeCallbacks(this)
                targetView = null

                if (!repeated) {
                    if (onClick != null) {
                        onClick.invoke(view)
                    } else {
                        onRepeat(view)
                    }
                }

                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                view.isPressed = false
                view.removeCallbacks(this)
                targetView = null
                return true
            }
        }

        return true
    }

    override fun run() {
        repeated = true

        val view = targetView ?: return
        view.postDelayed(this, repeatDelayMs)
        onRepeat(view)
    }

    private companion object {
        const val DEFAULT_INITIAL_DELAY_MS = 800L
        const val DEFAULT_REPEAT_DELAY_MS = 500L
    }
}