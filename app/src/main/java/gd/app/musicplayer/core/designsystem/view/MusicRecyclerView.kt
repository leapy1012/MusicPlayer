package gd.app.musicplayer.core.designsystem.view

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.recyclerview.widget.COUIRecyclerView
import androidx.recyclerview.widget.DefaultItemAnimator
import kotlin.math.abs

/**
 * Library / track lists: COUI spring overscroll + ViewPager-friendly touch routing.
 *
 * Only claims the gesture after a clear **vertical** drag so ViewPager2 can still
 * switch tabs on horizontal swipes. Change animations stay off to avoid flicker.
 */
class MusicRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : COUIRecyclerView(context, attrs, defStyleAttr) {

    private val touchSlop: Int = ViewConfiguration.get(context).scaledTouchSlop

    private var initialTouchX: Float = 0f
    private var initialTouchY: Float = 0f
    private var directionLocked: Boolean = false

    init {
        disableChangeAnimations()
        setOverScrollEnable(true)
        overScrollMode = OVER_SCROLL_ALWAYS
        isNestedScrollingEnabled = true
    }

    private fun disableChangeAnimations() {
        val animator = itemAnimator
        if (animator is DefaultItemAnimator) {
            animator.supportsChangeAnimations = false
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                initialTouchX = event.x
                initialTouchY = event.y
                directionLocked = false
                // Do not block the parent yet — let ViewPager2 compete for horizontal swipes.
                parent?.requestDisallowInterceptTouchEvent(false)
            }

            MotionEvent.ACTION_MOVE -> {
                if (!directionLocked) {
                    val deltaX = abs(event.x - initialTouchX)
                    val deltaY = abs(event.y - initialTouchY)
                    if (deltaX > touchSlop || deltaY > touchSlop) {
                        directionLocked = true
                        if (deltaY > deltaX) {
                            parent?.requestDisallowInterceptTouchEvent(true)
                        } else {
                            parent?.requestDisallowInterceptTouchEvent(false)
                        }
                    }
                }
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                directionLocked = false
            }
        }

        return super.dispatchTouchEvent(event)
    }
}
