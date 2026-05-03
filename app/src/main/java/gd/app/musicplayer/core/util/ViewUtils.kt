package gd.app.musicplayer.core.util

import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.view.MotionEvent
import android.view.TouchDelegate
import android.view.View
import android.view.ViewGroup
import android.view.ViewParent
import android.view.ViewTreeObserver
import androidx.core.graphics.drawable.DrawableCompat
import java.util.LinkedList

/** o8/a1 */
abstract class ViewUtils {

    class RunOnPreDrawOnceListener(
        private val targetView: View,
        private val action: Runnable
    ) : ViewTreeObserver.OnPreDrawListener {

        override fun onPreDraw(): Boolean {
            val observer = targetView.viewTreeObserver
            if (observer.isAlive) {
                observer.removeOnPreDrawListener(this)
            }
            action.run()
            return false
        }
    }

    class RunOnGlobalLayoutOnceListener(
        private val targetView: View,
        private val action: Runnable
    ) : ViewTreeObserver.OnGlobalLayoutListener {

        override fun onGlobalLayout() {
            val observer = targetView.viewTreeObserver
            if (observer.isAlive) {
                observer.removeOnGlobalLayoutListener(this)
            }
            action.run()
        }
    }

    interface ViewSkipPredicate {
        fun shouldSkip(target: Any?): Boolean
    }

    interface ViewVisitor {
        fun visit(view: View, isViewGroup: Boolean): Boolean
    }

    companion object {

        // original: a
        @JvmStatic
        fun consumeTouchEvent(view: View, event: MotionEvent): Boolean {
            return true
        }

        // original: b
        @JvmStatic
        fun applyExpandedTouchDelegate(targetView: View, extraPadding: Int, parentView: View) {
            val hitRect = Rect()
            targetView.getHitRect(hitRect)
            hitRect.left -= extraPadding
            hitRect.top -= extraPadding
            hitRect.right += extraPadding
            hitRect.bottom += extraPadding
            parentView.touchDelegate = TouchDelegate(hitRect, targetView)
        }

        // original: c
        @JvmStatic
        fun expandTouchArea(view: View?, extraPadding: Int) {
            if (view == null || view.parent == null) return

            val parentView = view.parent as View
            parentView.post {
                applyExpandedTouchDelegate(view, extraPadding, parentView)
            }
        }

        // original: d
        @JvmStatic
        fun findParentOfType(view: View, parentClass: Class<*>): Any? {
            var currentParent: ViewParent? = view.parent

            while (currentParent != null) {
                if (parentClass.isInstance(currentParent)) {
                    return currentParent
                }

                val nextParent = currentParent.parent
                if (currentParent == nextParent) {
                    return null
                }
                currentParent = nextParent
            }

            return null
        }

        // original: e
        @JvmStatic
        fun isGone(view: View): Boolean {
            return view.visibility == View.GONE
        }

        // original: f
        @JvmStatic
        fun isRtl(view: View): Boolean {
            return view.layoutDirection == View.LAYOUT_DIRECTION_RTL
        }

        // original: g
        @JvmStatic
        fun isVisible(view: View): Boolean {
            return view.visibility == View.VISIBLE
        }

        // original: h
        @JvmStatic
        fun traverseViewTreeBreadthFirst(rootView: View, visitor: ViewVisitor) {
            val queue = LinkedList<View>()
            queue.add(rootView)

            while (queue.isNotEmpty()) {
                val currentView = queue.removeAt(0)

                if (currentView !is ViewGroup) {
                    visitor.visit(currentView, false)
                } else if (!visitor.visit(currentView, true)) {
                    for (childIndex in 0 until currentView.childCount) {
                        queue.add(childIndex, currentView.getChildAt(childIndex))
                    }
                }
            }
        }

        // original: i
        @JvmStatic
        fun runOnGlobalLayoutOnce(view: View, action: Runnable) {
            val observer = view.viewTreeObserver
            if (observer.isAlive) {
                observer.addOnGlobalLayoutListener(
                    RunOnGlobalLayoutOnceListener(view, action)
                )
            }
        }

        // original: j
        @JvmStatic
        fun runOnPreDrawOnce(view: View, action: Runnable) {
            val observer = view.viewTreeObserver
            if (observer.isAlive) {
                observer.addOnPreDrawListener(
                    RunOnPreDrawOnceListener(view, action)
                )
            }
        }

        // original: k
        @JvmStatic
        fun removeFromParent(view: View?) {
            if (view != null) {
                val parent = view.parent
                if (parent is ViewGroup) {
                    parent.removeView(view)
                }
            }
        }

        // original: l
        @JvmStatic
        fun setGone(view: View, shouldBeGone: Boolean) {
            view.visibility = if (shouldBeGone) View.GONE else View.VISIBLE
        }

        // original: m
        @JvmStatic
        fun setInvisible(view: View, shouldBeInvisible: Boolean) {
            view.visibility = if (shouldBeInvisible) View.INVISIBLE else View.VISIBLE
        }

        // original: n
        @JvmStatic
        fun setTouchBlocked(view: View, shouldBlockTouches: Boolean) {
            view.setOnTouchListener(
                if (shouldBlockTouches) {
                    View.OnTouchListener { touchedView, motionEvent ->
                        consumeTouchEvent(touchedView, motionEvent)
                    }
                } else {
                    null
                }
            )
        }

        // original: o
        @JvmStatic
        fun tintBackground(view: View, tintColor: Int) {
            val background = view.background ?: return
            val wrappedDrawable = DrawableCompat.wrap(background)
            DrawableCompat.setTint(wrappedDrawable, tintColor)
            setBackground(view, wrappedDrawable)
        }

        // original: p
        @JvmStatic
        fun setBackground(view: View, background: Drawable?) {
            view.background = background
        }

        // original: q
        @JvmStatic
        fun setEnabledRecursively(view: View, isEnabled: Boolean) {
            setEnabledRecursively(view, isEnabled, null)
        }

        // original: r
        @JvmStatic
        fun setEnabledRecursively(
            view: View,
            isEnabled: Boolean,
            skipPredicate: ViewSkipPredicate?
        ) {
            if (skipPredicate == null || !skipPredicate.shouldSkip(view)) {
                view.isEnabled = isEnabled
            }

            if (view is ViewGroup) {
                for (childIndex in 0 until view.childCount) {
                    val child = view.getChildAt(childIndex)
                    if (skipPredicate == null || !skipPredicate.shouldSkip(child)) {
                        setEnabledRecursively(child, isEnabled, skipPredicate)
                    }
                }
            }
        }

        // original: s
        @JvmStatic
        fun setSelectedRecursively(view: View, isSelected: Boolean) {
            setSelectedRecursively(view, isSelected, null)
        }

        // original: t
        @JvmStatic
        fun setSelectedRecursively(
            view: View,
            isSelected: Boolean,
            skipPredicate: ViewSkipPredicate?
        ) {
            if (skipPredicate == null || !skipPredicate.shouldSkip(view)) {
                view.isSelected = isSelected
            }

            if (view is ViewGroup) {
                for (childIndex in 0 until view.childCount) {
                    val child = view.getChildAt(childIndex)
                    if (skipPredicate == null || !skipPredicate.shouldSkip(child)) {
                        setSelectedRecursively(child, isSelected, skipPredicate)
                    }
                }
            }
        }
    }
}
