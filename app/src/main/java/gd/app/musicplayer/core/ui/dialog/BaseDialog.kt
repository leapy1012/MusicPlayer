package gd.app.musicplayer.core.ui.dialog

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import gd.app.lib.configuration.CommonContainerLayout
import gd.app.lib.configuration.ConfigurationFrameLayout
import gd.app.lib.view.InterceptTouchCoordinatorLayout
import gd.app.musicplayer.R
import gd.app.musicplayer.core.extension.updateWidth

abstract class BaseDialog(
    context: Context,
    protected val config: Config
) : Dialog(
    context,
    if (config.windowThemeRes == 0) R.style.CommonDialog else config.windowThemeRes
), ConfigurationFrameLayout.OnConfigurationChangeListener {

    val dialogKey: String = config.cacheKey(context)

    protected val rootContainer: FrameLayout
    protected val contentHost: FrameLayout
    protected val touchInterceptorLayout: InterceptTouchCoordinatorLayout
    protected val dialogContainer: CommonContainerLayout

    protected var dialogContentView: View
    protected var bottomSheetBehavior: BottomSheetBehavior<View>? = null

    private val bottomSheetCallback = object : BottomSheetBehavior.BottomSheetCallback() {
        override fun onStateChanged(bottomSheet: View, newState: Int) {
            if (newState == BottomSheetBehavior.STATE_HIDDEN) {
                dismiss()
            }
        }

        override fun onSlide(bottomSheet: View, slideOffset: Float) = Unit
    }

    init {
        DialogRegistry.register(this)

        window?.let { DialogWindowConfigurator.configureBeforeContent(it, config) }

        setContentView(
            if (config.isBottomSheetStyle) {
                R.layout.common_dialog_layout_full
            } else {
                R.layout.common_dialog_layout
            }
        )

        rootContainer = findViewById(R.id.common_dialog_content)
        touchInterceptorLayout = findViewById(R.id.common_dialog_coordinator)
        dialogContainer = findViewById(R.id.common_dialog_background)
        contentHost = findViewById(R.id.common_dialog_container)

        touchInterceptorLayout.setInterceptTouchEvent(!config.allowTouchEventPassThrough)
        dialogContainer.setOnConfigurationChangeListener(this)

        dialogContentView = createContentView(context, config)

        if (config.attachToBottom) {
            setupBottomSheetLayout()
        } else {
            setupCenteredLayout()
        }

        applyDialogAppearance()
    }

    abstract fun createContentView(context: Context, config: Config): View

    private fun setupBottomSheetLayout() {
        if (config.isBottomSheetStyle) {
            bottomSheetBehavior = BottomSheetBehavior<View>().apply {
                isHideable = config.hideableBySwipe && config.cancelable
                state = BottomSheetBehavior.STATE_EXPANDED
                peekHeight = BottomSheetBehavior.PEEK_HEIGHT_AUTO
                skipCollapsed = true
            }
        }

        dialogContainer.layoutParams = CoordinatorLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = 49
            if (config.isBottomSheetStyle) {
                behavior = bottomSheetBehavior
            }
        }

        contentHost.layoutParams = FrameLayout.LayoutParams(
            DialogWidthResolver.resolve(context, config.widthPx),
            config.heightPx
        ).apply {
            gravity = 81
        }

        contentHost.addView(
            dialogContentView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                config.heightPx
            )
        )

        if (config.isBottomSheetStyle) {
            installBottomSheetAccessibility()
        }
    }

    private fun setupCenteredLayout() {
        dialogContainer.layoutParams = CoordinatorLayout.LayoutParams(
            DialogWidthResolver.resolve(context, config.widthPx),
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = 17
        }

        contentHost.addView(
            dialogContentView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                config.heightPx
            )
        )
    }

    private fun installBottomSheetAccessibility() {
        ViewCompat.setAccessibilityDelegate(
            dialogContainer,
            object : AccessibilityDelegateCompat() {
                override fun onInitializeAccessibilityNodeInfo(
                    host: View,
                    info: AccessibilityNodeInfoCompat
                ) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.isDismissable = config.cancelable
                    if (config.cancelable) {
                        info.addAction(AccessibilityNodeInfoCompat.ACTION_DISMISS)
                    }
                }

                override fun performAccessibilityAction(
                    host: View,
                    action: Int,
                    args: Bundle?
                ): Boolean {
                    if (action == AccessibilityNodeInfoCompat.ACTION_DISMISS && config.cancelable) {
                        dismiss()
                        return true
                    }
                    return super.performAccessibilityAction(host, action, args)
                }
            }
        )
    }

    private fun applyDialogAppearance() {
        setCancelable(config.cancelable)
        setCanceledOnTouchOutside(config.canceledOnTouchOutside)

        rootContainer.findViewById<View?>(R.id.common_dialog_outside)
            ?.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN &&
                    config.cancelable &&
                    config.canceledOnTouchOutside
                ) {
                    dismiss()
                }
                true
            }

        dialogContainer.background = config.backgroundDrawable ?: Color.WHITE.toDrawable()
        config.cornerRadii?.let(dialogContainer::setRadiusArray)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        bottomSheetBehavior?.addBottomSheetCallback(bottomSheetCallback)
    }

    override fun onDetachedFromWindow() {
        bottomSheetBehavior?.removeBottomSheetCallback(bottomSheetCallback)
        super.onDetachedFromWindow()
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        val listener = config.onKeyListener
        return if (listener != null && listener.onKey(this, keyCode, event)) {
            true
        } else {
            super.onKeyUp(keyCode, event)
        }
    }

    override fun onSaveInstanceState(): Bundle {
        return if (config.allowFrameworkStateSaving) {
            super.onSaveInstanceState()
        } else {
            Bundle()
        }
    }

    override fun show() {
        val currentWindow = window
        if (currentWindow != null && config.secureWindow) {
            currentWindow.setFlags(8, 8)
        }

        super.show()

        if (currentWindow != null && config.secureWindow) {
            currentWindow.clearFlags(8)
        }

        config.onShowListener?.onShow(this)
    }

    override fun dismiss() {
        try {
            DialogRegistry.unregister(dialogKey)

            config.onDismissListener?.onDismiss(this)

            val behavior = bottomSheetBehavior
            if (behavior != null &&
                behavior.state != BottomSheetBehavior.STATE_HIDDEN &&
                behavior.isHideable
            ) {
                behavior.state = BottomSheetBehavior.STATE_HIDDEN
                return
            }

            super.dismiss()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        if (config.widthPx != Config.WIDTH_90_PERCENT) return

        val recalculatedWidth = DialogWidthResolver.resolve(
            context = context,
            widthPx = config.widthPx,
            configuration = newConfig
        )

        if (!config.isBottomSheetStyle) {
            window?.let { currentWindow ->
                currentWindow.attributes?.let { params ->
                    params.width = recalculatedWidth
                    currentWindow.attributes = params
                }
            }
            dialogContainer.updateWidth(recalculatedWidth)
            return
        }

        if (config.attachToBottom) {
            contentHost.updateWidth(recalculatedWidth)
        } else {
            dialogContainer.updateWidth(recalculatedWidth)
        }
    }

    companion object {
        fun dismissAll() = DialogRegistry.dismissAll()
        fun dismissAll(activity: Activity) = DialogRegistry.dismissAll(activity)
        fun dismiss(activity: Activity, config: Config) = DialogRegistry.dismiss(activity, config)
        fun dismiss(dialogKey: String) = DialogRegistry.dismiss(dialogKey)
    }

    open class Config {
        var windowThemeRes: Int = R.style.CommonDialog
        var widthPx: Int = WIDTH_90_PERCENT
        var heightPx: Int = ViewGroup.LayoutParams.WRAP_CONTENT

        var backgroundDrawable: Drawable? = null
        var dimAmount: Float = 0.35f
        var gravity: Int = 0

        var softInputMode: Int = -1
        var layoutInDisplayCutoutMode: Int = -1

        var layoutProvider: LayoutProvider = DefaultLayoutProvider()
        var dialogLayoutRes: Int = 0

        var contentLeftPaddingPx: Int = 0
        var contentRightPaddingPx: Int = 0
        var contentTopPaddingPx: Int = 0
        var contentBottomPaddingPx: Int = 0

        var cancelable: Boolean = true
        var canceledOnTouchOutside: Boolean = true

        var onDismissListener: DialogInterface.OnDismissListener? = null
        var onShowListener: DialogInterface.OnShowListener? = null
        var onKeyListener: DialogInterface.OnKeyListener? = null

        var cacheTag: String? = null

        var manageSystemBars: Boolean = false
        var secureWindow: Boolean = false
        var lightStatusBarIcons: Boolean = false
        var statusBarColor: Int = 0
        var fitsStatusBar: Boolean = true
        var lightNavigationBarIcons: Boolean = false
        var navigationBarColor: Int = 0

        var requestNoTitleFeature: Boolean = true
        var isBottomSheetStyle: Boolean = false
        var allowTouchEventPassThrough: Boolean = true
        var attachToBottom: Boolean = false
        var hideableBySwipe: Boolean = true
        var allowFrameworkStateSaving: Boolean = true

        var cornerRadii: FloatArray? = null
        var windowAnimationRes: Int = -1

        private var cachedKey: String? = null

        fun applyBottomSheetMode(enabled: Boolean) {
            backgroundDrawable = Color.WHITE.toDrawable()
            cancelable = true
            canceledOnTouchOutside = true
            dimAmount = 0.35f
            heightPx = ViewGroup.LayoutParams.WRAP_CONTENT

            if (!enabled) {
                isBottomSheetStyle = false
                widthPx = WIDTH_90_PERCENT
                windowAnimationRes = R.style.DialogAnim
                windowThemeRes = R.style.CommonDialog
                return
            }

            isBottomSheetStyle = true
            attachToBottom = true
            widthPx = ViewGroup.LayoutParams.MATCH_PARENT
            manageSystemBars = true
            lightNavigationBarIcons = false
            statusBarColor = 0
            fitsStatusBar = true
            navigationBarColor = 0
            lightStatusBarIcons = false
            windowAnimationRes = R.style.BottomDialogAnim
            windowThemeRes = R.style.CommonDialog_TranslucentStyle
        }

        fun cacheKey(context: Context): String {
            if (cachedKey == null) {
                cachedKey = if (cacheTag == null) {
                    context.toString() + toString().hashCode()
                } else {
                    context.toString() + cacheTag
                }
            }
            return cachedKey!!
        }

        companion object {
            const val WIDTH_90_PERCENT = -10
        }
    }

    interface LayoutProvider {
        fun getLayoutRes(config: Config): Int
    }

    class DefaultLayoutProvider : LayoutProvider {
        override fun getLayoutRes(config: Config): Int = config.dialogLayoutRes
    }
}
