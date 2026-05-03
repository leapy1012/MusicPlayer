package gd.app.musicplayer.ui.common.model

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.view.View
import androidx.annotation.Keep

class ViewFlipHelper {

    private val views = mutableListOf<View>()

    private var previousIndex = 0
    private var currentIndex = 0
    private var flipAnimator: ObjectAnimator? = null

    fun bindViews(rootView: View, vararg viewIds: Int) {
        views.clear()

        viewIds.forEachIndexed { index, viewId ->
            val childView = rootView.findViewById<View>(viewId)

            childView.visibility =
                if (index == currentIndex) View.VISIBLE else View.GONE

            views.add(childView)
        }
    }

    fun getCurrentIndex(): Int {
        return currentIndex
    }

    fun flipTo(index: Int) {
        if (index == currentIndex || index !in views.indices) return

        previousIndex = currentIndex
        currentIndex = index

        flipAnimator?.apply {
            removeAllListeners()
            cancel()
        }

        flipAnimator = ObjectAnimator.ofFloat(
            this,
            PROPERTY_FLIP_PERCENT,
            0f,
            1f
        ).apply {
            duration = FLIP_DURATION_MS
            addListener(createFlipAnimatorListener())
            start()
        }
    }

    private fun createFlipAnimatorListener(): Animator.AnimatorListener {
        return object : AnimatorListenerAdapter() {

            override fun onAnimationStart(animation: Animator) {
                views[previousIndex].apply {
                    alpha = 1f
                    visibility = View.VISIBLE
                }

                views[currentIndex].apply {
                    alpha = 0f
                    visibility = View.VISIBLE
                }
            }

            override fun onAnimationEnd(animation: Animator) {
                views[previousIndex].apply {
                    alpha = 0f
                    visibility = View.GONE
                }

                views[currentIndex].apply {
                    alpha = 1f
                    visibility = View.VISIBLE
                }
            }
        }
    }

    @Keep
    fun setFlipPercent(percent: Float) {
        if (previousIndex !in views.indices || currentIndex !in views.indices) return

        views[previousIndex].alpha = 1f - percent
        views[currentIndex].alpha = percent
    }

    fun release() {
        flipAnimator?.apply {
            removeAllListeners()
            cancel()
        }
        flipAnimator = null
        views.clear()
    }

    private companion object {
        const val PROPERTY_FLIP_PERCENT = "flipPercent"
        const val FLIP_DURATION_MS = 150L
    }
}
