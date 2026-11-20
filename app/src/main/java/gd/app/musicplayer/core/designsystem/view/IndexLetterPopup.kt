package gd.app.musicplayer.core.designsystem.view

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import gd.app.musicplayer.R

/**
 * Port of `y7.a`.
 *
 * A centred letter plate attached directly to `android.R.id.content` while the index bar is being
 * dragged, and detached [HIDE_DELAY_MS] after the finger lifts.
 */
class IndexLetterPopup private constructor(activity: Activity) {

    private val container: ViewGroup = activity.findViewById(android.R.id.content)
    private val popupView: View =
        activity.layoutInflater.inflate(R.layout.layout_index_letter_popup, container, false)
    private val message: TextView = popupView.findViewById(R.id.message)

    private val handler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { hide() }

    private var attached = false

    fun setLetter(letter: String) {
        message.text = letter
    }

    /** `y7.a.f`: cancel any pending hide and attach the plate once. */
    fun show() {
        handler.removeCallbacks(hideRunnable)
        if (attached) return
        container.addView(popupView)
        attached = true
    }

    /** `y7.a.d`: linger for a second after the drag ends. */
    fun hideDelayed() {
        handler.removeCallbacks(hideRunnable)
        handler.postDelayed(hideRunnable, HIDE_DELAY_MS)
    }

    fun hide() {
        handler.removeCallbacks(hideRunnable)
        if (!attached) return
        container.removeView(popupView)
        attached = false
    }

    companion object {
        private const val HIDE_DELAY_MS = 1_000L

        /** Mirrors `index.c`'s constructor: only available when the view lives in an Activity. */
        fun createOrNull(context: Context): IndexLetterPopup? {
            var current = context
            while (current is ContextWrapper) {
                if (current is Activity) return IndexLetterPopup(current)
                current = current.baseContext
            }
            return null
        }
    }
}
