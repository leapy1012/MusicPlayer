package gd.app.lib.configuration
import android.content.Context
import android.content.res.Configuration
import android.util.AttributeSet
import android.widget.LinearLayout

open class ConfigurationLinearLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private var configurationChangeListener: OnConfigurationChangeListener? = null
    private var sizeChangedListener: OnSizeChangedListener? = null

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        configurationChangeListener?.onConfigurationChanged(newConfig)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        sizeChangedListener?.onSizeChanged(this, w, h)
    }

    fun setOnConfigurationChangeListener(listener: OnConfigurationChangeListener?) {
        configurationChangeListener = listener
    }

    fun setOnViewSizeChangeListener(listener: OnSizeChangedListener?) {
        sizeChangedListener = listener
    }

    fun setOnAttachChangeListener(listener: OnAttachChangeListener?) {
        // Intentionally empty, same as original Java
    }

    interface OnConfigurationChangeListener {
        fun onConfigurationChanged(newConfig: Configuration)
    }

    interface OnSizeChangedListener {
        fun onSizeChanged(view: LinearLayout, width: Int, height: Int)
    }

    interface OnAttachChangeListener
}