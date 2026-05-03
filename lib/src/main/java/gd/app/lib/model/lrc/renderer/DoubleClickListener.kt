package gd.app.lib.model.lrc.renderer

import android.view.View

abstract class DoubleClickListener : View.OnClickListener {

    private var lastView: View? = null
    private var isWaitingSecondClick = false

    private val resetRunnable = Runnable {
        isWaitingSecondClick = false
        lastView?.let { onSingleClick(it) }
    }

    protected abstract fun onDoubleClick(view: View)

    protected open fun onSingleClick(view: View) {}

    override fun onClick(view: View) {
        lastView = view

        if (!isWaitingSecondClick) {
            isWaitingSecondClick = true
            view.postDelayed(resetRunnable, 300)
        } else {
            view.removeCallbacks(resetRunnable)
            isWaitingSecondClick = false
            onDoubleClick(view)
        }
    }
}