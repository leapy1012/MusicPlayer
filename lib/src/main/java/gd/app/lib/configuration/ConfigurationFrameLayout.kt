package gd.app.lib.configuration

import android.content.Context
import android.content.res.Configuration
import android.util.AttributeSet
import android.widget.FrameLayout

open class ConfigurationFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var onConfigurationChangeListener: OnConfigurationChangeListener? = null
    private var onViewSizeChangeListener: OnViewSizeChangeListener? = null
    private var onAttachChangeListener: OnAttachChangeListener? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        onAttachChangeListener?.onAttached(this)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        onAttachChangeListener?.onDetached(this)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        onConfigurationChangeListener?.onConfigurationChanged(newConfig)
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        onViewSizeChangeListener?.onSizeChanged(this, width, height)
    }

    fun setOnConfigurationChangeListener(listener: OnConfigurationChangeListener?) {
        onConfigurationChangeListener = listener
    }

    fun setOnViewSizeChangeListener(listener: OnViewSizeChangeListener?) {
        onViewSizeChangeListener = listener
    }

    fun setOnAttachChangeListener(listener: OnAttachChangeListener?) {
        onAttachChangeListener = listener
    }

    fun interface OnConfigurationChangeListener {
        fun onConfigurationChanged(newConfig: Configuration)
    }

    fun interface OnViewSizeChangeListener {
        fun onSizeChanged(view: ConfigurationFrameLayout, width: Int, height: Int)
    }

    interface OnAttachChangeListener {
        fun onAttached(view: ConfigurationFrameLayout) {}
        fun onDetached(view: ConfigurationFrameLayout) {}
    }
}