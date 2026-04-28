package gd.app.musicplayer.core.ui.view

import android.content.Context
import android.content.ContextWrapper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import kotlin.math.abs

/**
 *   Logic and behavior:
 *
 *   - On ACTION_DOWN, it tells the parent not to intercept touch yet.
 *   - It stores the first touch position.
 *   - On move, once the finger moves beyond touchSlop, it decides gesture direction.
 *   - If horizontal movement is stronger than vertical movement, it gives touch handling back to the parent.
 *   - If vertical movement is stronger, the RecyclerView keeps handling the scroll.
 *   - It also disables RecyclerView change animations to avoid flicker/blink when list items update.
 *
 *   Customized reason:
 *
 *   - This class exists to resolve nested scroll conflict.
 *   - A music list is usually vertical, but it is often placed inside a parent that may swipe horizontally, such as a ViewPager, sliding panel, or tab container.
 *   - The customization makes vertical song scrolling feel natural while still allowing horizontal parent gestures.
 *   - Disabling change animations is likely intentional for music lists because play-state, selection-state, or metadata updates happen often, and default item change animations can look
 *     jumpy.
 *     */

class MusicRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {

    private val touchSlop: Int = ViewConfiguration.get(context).scaledTouchSlop

    private var initialTouchX: Float = 0f
    private var initialTouchY: Float = 0f
    private var basePaddingLeft: Int = paddingLeft
    private var basePaddingTop: Int = paddingTop
    private var basePaddingRight: Int = paddingRight
    private var basePaddingBottom: Int = paddingBottom
    private var playerSheetBottomInset: Int = 0
    private var applyingCombinedPadding: Boolean = false

    init {
        disableChangeAnimations()
        clipToPadding = false
    }

    private fun disableChangeAnimations() {
        (itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        syncPlayerSheetInsetFromHost()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                initialTouchX = event.x
                initialTouchY = event.y
            }

            MotionEvent.ACTION_MOVE -> {
                if (initialTouchX != 0f || initialTouchY != 0f) {
                    val deltaX = abs(event.x - initialTouchX)
                    val deltaY = abs(event.y - initialTouchY)

                    if (deltaX >= touchSlop || deltaY >= touchSlop) {
                        if (deltaX > deltaY) {
                            parent?.requestDisallowInterceptTouchEvent(false)
                        }

                        initialTouchX = 0f
                        initialTouchY = 0f
                    }
                }
            }
        }

        return super.dispatchTouchEvent(event)
    }

    override fun setPadding(left: Int, top: Int, right: Int, bottom: Int) {
        if (applyingCombinedPadding) {
            super.setPadding(left, top, right, bottom)
            return
        }

        basePaddingLeft = left
        basePaddingTop = top
        basePaddingRight = right
        basePaddingBottom = bottom
        applyCombinedPadding()
    }

    fun setPlayerSheetBottomInset(inset: Int) {
        val normalizedInset = inset.coerceAtLeast(0)
        if (playerSheetBottomInset == normalizedInset) return

        playerSheetBottomInset = normalizedInset
        applyCombinedPadding()
    }

    private fun applyCombinedPadding() {
        applyingCombinedPadding = true
        super.setPadding(
            basePaddingLeft,
            basePaddingTop,
            basePaddingRight,
            basePaddingBottom + playerSheetBottomInset
        )
        applyingCombinedPadding = false
    }

    private fun syncPlayerSheetInsetFromHost() {
        var current: Context? = context
        while (current is ContextWrapper) {
            if (current is PlayerSheetInsetHost) {
                setPlayerSheetBottomInset(current.currentPlayerSheetVisibleHeight())
                return
            }
            current = current.baseContext
        }
    }
}
