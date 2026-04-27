package gd.app.musicplayer.ui.common.view
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.animation.LinearInterpolator
import androidx.annotation.Keep
import androidx.appcompat.widget.AppCompatImageView
import gd.app.musicplayer.data.model.Music
import androidx.core.graphics.withRotation

class RotationalImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AppCompatImageView(context, attrs) {

    companion object {
        private const val ROTATION_DURATION_MS = 30_000L
        private const val MAX_ROTATION_DEGREES = 359f
    }

    private var isAttachedToWindowFlag = false
    private var currentAnimatorPlayTime = 0L
    private var currentRotationDegrees = 0f
    private var currentMusic: Music? = null
    private var isRotationEnabled = false

    private val rotationAnimator: ObjectAnimator = ObjectAnimator.ofFloat(
        this,
        "degrees",
        0f,
        MAX_ROTATION_DEGREES
    ).apply {
        interpolator = LinearInterpolator()
        repeatCount = ObjectAnimator.INFINITE
        duration = ROTATION_DURATION_MS
    }

    private fun updateAnimatorState() {
        if (isRotationEnabled && isAttachedToWindowFlag) {
            resumeAnimator()
        } else {
            pauseAnimator()
        }
    }

    private fun pauseAnimator() {
        if (!rotationAnimator.isRunning) return
        currentAnimatorPlayTime = rotationAnimator.currentPlayTime
        rotationAnimator.cancel()
    }

    private fun resumeAnimator() {
        if (rotationAnimator.isRunning) return
        rotationAnimator.start()
        rotationAnimator.currentPlayTime = currentAnimatorPlayTime
    }

    private fun resetAnimator() {
        if (rotationAnimator.isRunning) {
            rotationAnimator.cancel()
            rotationAnimator.start()
        } else {
            currentAnimatorPlayTime = 0L
            setDegrees(0f)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isAttachedToWindowFlag = true
        updateAnimatorState()
    }

    override fun onDetachedFromWindow() {
        isAttachedToWindowFlag = false
        updateAnimatorState()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.withRotation(currentRotationDegrees, width / 2f, height / 2f) {
            super.onDraw(this)
        }
    }

    fun resetStateIfMusicChanged(newMusic: Music?) {
        if (newMusic == currentMusic) return
        currentMusic = newMusic
        resetAnimator()
    }

    @Keep
    fun setDegrees(degrees: Float) {
        if (currentRotationDegrees == degrees) return
        currentRotationDegrees = degrees
        invalidate()
    }

    fun setRotateEnabled(enabled: Boolean) {
        if (isRotationEnabled == enabled) return
        isRotationEnabled = enabled
        updateAnimatorState()
    }
}