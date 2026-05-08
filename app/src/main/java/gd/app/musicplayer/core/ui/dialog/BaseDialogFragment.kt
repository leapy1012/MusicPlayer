package gd.app.musicplayer.core.ui.dialog

import android.content.DialogInterface
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.EditText
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import gd.app.musicplayer.R
import gd.app.musicplayer.core.extension.getMinScreenSize
import gd.app.musicplayer.core.theme.ThemePalette
import gd.app.musicplayer.core.theme.accentColor
import gd.app.musicplayer.core.theme.cancelBaseColor
import gd.app.musicplayer.core.theme.cancelTextColor
import gd.app.musicplayer.core.theme.confirmRippleColor
import gd.app.musicplayer.core.theme.dividerColor
import gd.app.musicplayer.core.theme.messageColor
import gd.app.musicplayer.core.theme.rippleColor
import gd.app.musicplayer.core.theme.titleColor
import gd.app.musicplayer.core.ui.drawable.DrawableUtil
import gd.app.musicplayer.core.ui.drawable.ViewStateDrawables
import gd.app.musicplayer.core.ui.view.SeekBar
import gd.app.musicplayer.ui.common.base.BaseActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import gd.app.musicplayer.ui.theme.ThemeEngine
import javax.inject.Inject

open class BaseDialogFragment : DialogFragment() {

    @Inject
    lateinit var themeEngine: ThemeEngine
    private var onDismissListener: DialogInterface.OnDismissListener? = null
    private var isViewDestroyed = true
    private val pendingUiActions = ArrayDeque<() -> Unit>()

    protected val baseActivity: BaseActivity
        get() = requireActivity() as BaseActivity

    protected fun isFragmentTransactionSafe(): Boolean {
        val activity = activity ?: return false
        return !isDialogDestroyed() &&
                !activity.isFinishing &&
                lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) &&
                !parentFragmentManager.isStateSaved
    }

    protected fun runWhenFragmentTransactionSafe(action: () -> Unit) {
        if (isFragmentTransactionSafe()) {
            action()
        } else {
            pendingUiActions += action
        }
    }

    private fun flushPendingUiActions() {
        while (pendingUiActions.isNotEmpty() && isFragmentTransactionSafe()) {
            pendingUiActions.removeFirst().invoke()
        }
    }

    protected open fun applyBackground(view: View) {
        view.background = provideBackgroundDrawable()
    }

    protected open fun provideBackgroundDrawable(): Drawable {
        return themeEngine.currentTheme()
            .getDialogSurfaceDrawable(requireContext())
    }

    protected open fun transformRootView(view: View): View = view
    protected open fun shouldApplyTagStyles(): Boolean = true

    protected open fun provideDimAmount(): Float = 0.35f

    protected open fun provideGravity(): Int = Gravity.CENTER

    protected open fun provideWidth(configuration: Configuration): Int {
        return (baseActivity.getMinScreenSize() * 0.9f).toInt()
    }

    protected open fun provideHeight(configuration: Configuration): Int {
        return ViewGroup.LayoutParams.WRAP_CONTENT
    }

    protected open fun provideSoftInputMode(): Int = -1

    protected open fun provideWindowAnimation(): Int = R.style.DialogAnim

    protected open fun shouldAllowOutsideTouchCancel(): Boolean = true

    fun isDialogDestroyed(): Boolean = isViewDestroyed

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, R.style.AppDialogTheme)
        showsDialog = true
        isCancelable = true
        isViewDestroyed = false
    }

    override fun onGetLayoutInflater(savedInstanceState: Bundle?): LayoutInflater {
        return super.onGetLayoutInflater(savedInstanceState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        isViewDestroyed = false
        applyBackground(transformRootView(view))
        if (shouldApplyTagStyles()) {
            applyTagStyles(view)
        }
        super.onViewCreated(view, savedInstanceState)
    }

    override fun onStart() {
        super.onStart()

        val dialog = dialog ?: return
        val window = dialog.window ?: return

        updateWindowLayout(window)

        window.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        dialog.setCanceledOnTouchOutside(shouldAllowOutsideTouchCancel())

        flushPendingUiActions()
    }

    override fun onResume() {
        super.onResume()
        flushPendingUiActions()
    }

    override fun onDestroyView() {
        isViewDestroyed = true
        pendingUiActions.clear()
        super.onDestroyView()
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        onDismissListener?.onDismiss(dialog)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        dialog?.window?.let(::updateWindowLayout)
    }

    override fun dismiss() {
        dismissAllowingStateLoss()
    }

    override fun dismissAllowingStateLoss() {
        if (activity == null) {
            super.dismissAllowingStateLoss()
            return
        }

        runWhenFragmentTransactionSafe {
            performDismissAllowingStateLoss()
        }
    }

    fun showSafely(manager: FragmentManager, tag: String) {
        runWhenFragmentTransactionSafe {
            if (!isAdded && !manager.isStateSaved) {
                show(manager, tag)
            }
        }
    }

    override fun show(transaction: FragmentTransaction, tag: String?): Int {
        return if (parentFragmentManager.isStateSaved) {
            -1
        } else {
            super.show(transaction, tag)
        }
    }

    override fun show(manager: FragmentManager, tag: String?) {
        if (manager.isStateSaved) return
        super.show(manager, tag)
    }

    override fun showNow(manager: FragmentManager, tag: String?) {
        if (manager.isStateSaved) return
        super.showNow(manager, tag)
    }

    fun setOnDismissListener(listener: DialogInterface.OnDismissListener?) {
        onDismissListener = listener
    }

    protected fun <T> runDialogWork(
        work: suspend () -> T,
        onResult: (T) -> Unit
    ) {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { work() }
            if (!isAdded || view == null) return@launch
            onResult(result)
        }
    }

    protected fun performDismissAllowingStateLoss() {
        super.dismissAllowingStateLoss()
    }

    protected fun currentAccentColor(): Int = currentTheme().accentColor

    protected fun currentTheme(): ThemePalette {
        return themeEngine.currentTheme()
    }

    protected fun applyDialogBackground(
        rootView: View,
        reqWidth: Int = maxOf(320, resources.displayMetrics.widthPixels / 2),
        reqHeight: Int = maxOf(220, resources.displayMetrics.heightPixels / 3)
    ) {
        rootView.background = currentTheme().getDialogSurfaceDrawable(rootView.context)
    }

    protected fun applyDialogWidth(widthRatio: Float) {
        dialog?.window?.let { window ->
            val context = window.context
            val width = (context.resources.displayMetrics.widthPixels * widthRatio).toInt()
            window.attributes = window.attributes.apply {
                gravity = Gravity.CENTER
                this.width = width
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                dimAmount = 0.5f
            }
            window.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        }
    }

    protected fun applyTagStyles(rootView: View, accentColor: Int = currentAccentColor()) {
        val theme = currentTheme()
        val palette = DialogTagPalette(
            theme = theme,
            accentColorOverride = accentColor,
            editTextBackground = theme.getEditTextBackground(rootView.context)
        )
        applyTaggedStylesRecursive(rootView, palette)
    }

    private fun applyTaggedStylesRecursive(view: View, palette: DialogTagPalette) {
        applyTaggedStyle(tag = view.tag as? String, view = view, palette = palette)
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                applyTaggedStylesRecursive(view.getChildAt(index), palette)
            }
        }
    }

    protected open fun applyTaggedStyle(
        tag: String?,
        view: View,
        palette: DialogTagPalette
    ): Boolean {
        return when (tag) {
            "dialogTitle", "dialogTitleColor", "dialogTitleIcon", "dialogItem" -> {
                when (view) {
                    is TextView -> view.setTextColor(palette.titleColor)
                    is ImageView -> view.imageTintList = ColorStateList.valueOf(palette.titleColor)
                }
                true
            }

            "dialogMessage", "dialogMessageColor" -> {
                when (view) {
                    is TextView -> view.setTextColor(palette.messageColor)
                    is ImageView -> view.imageTintList =
                        ColorStateList.valueOf(palette.messageColor)
                }
                true
            }

            "dialogFavorite" -> {
                if (view is ImageView) {
                    view.imageTintList = ColorStateList(
                        arrayOf(intArrayOf(android.R.attr.state_selected), intArrayOf()),
                        intArrayOf(palette.accentColor, palette.titleColor)
                    )
                }
                true
            }

            "dialogButton" -> {
                if (view is TextView) {
                    view.setTextColor(palette.accentColor)
                }
                view.background = DrawableUtil.rectRipple(
                    fillColor = Color.TRANSPARENT,
                    rippleColor = palette.rippleColor
                )
                true
            }

            "dialogConfirm" -> {
                if (view is TextView) {
                    view.setTextColor(Color.WHITE)
                }
                view.background = DrawableUtil.roundedRipple(
                    fillColor = palette.accentColor,
                    rippleColor = palette.confirmRippleColor,
                    radius = 1000f
                )
                true
            }

            "dialogCancel" -> {
                if (view is TextView) {
                    view.setTextColor(palette.cancelTextColor)
                }
                view.background = DrawableUtil.roundedRipple(
                    fillColor = palette.cancelBaseColor,
                    rippleColor = palette.rippleColor,
                    radius = 1000f
                )
                true
            }

            "dialogItemBackground" -> {
                view.background = DrawableUtil.rectRipple(
                    fillColor = Color.TRANSPARENT,
                    rippleColor = palette.rippleColor
                )
                true
            }

            "dialogSelectBox" -> {
                if (view is ImageView) {
                    view.imageTintList = createSelectBoxTintList(
                        normalColor = palette.selectBoxNormalColor,
                        accentColor = palette.accentColor
                    )
                }
                true
            }

            "dialogDivider", "dialogDividerColor" -> {
                if (view is ListView) {
                    view.divider = palette.dividerColor.toDrawable()
                    if (view.dividerHeight <= 0) view.dividerHeight = 1
                } else {
                    view.setBackgroundColor(palette.dividerColor)
                }
                true
            }

            "dialogEditText" -> {
                if (view is EditText) {
                    view.setTextColor(palette.titleColor)
                    view.setHintTextColor(ColorUtils.setAlphaComponent(palette.titleColor, 128))
                    view.highlightColor = ColorUtils.setAlphaComponent(palette.accentColor, 77)

                    val background = DrawableCompat.wrap(
                        view.background?.mutate() ?: palette.editTextBackground.mutate()
                    )
                    DrawableCompat.setTintList(
                        background,
                        ViewStateDrawables.focusedDefaultColors(
                            ColorUtils.setAlphaComponent(palette.titleColor, 77),
                            palette.accentColor
                        )
                    )
                    view.background = background
                }
                true
            }

            "dialogSeekBar" -> {
                if (view is SeekBar) {
                    view.setThumbColor(palette.accentColor)
                    view.setProgressDrawable(
                        DrawableUtil.roundedProgress(
                            ColorUtils.setAlphaComponent(palette.titleColor, 77),
                            palette.accentColor,
                            (view.context.resources.displayMetrics.density * 4f).toInt()
                        )
                    )
                }
                true
            }

            "speedItemDes" -> {
                if (view is TextView) {
                    view.setTextColor(ColorUtils.setAlphaComponent(palette.titleColor, 160))
                }
                true
            }

            "speedItemText" -> {
                if (view is TextView) {
                    view.setTextColor(
                        ViewStateDrawables.selectedDefaultColors(
                            ColorUtils.setAlphaComponent(palette.titleColor, 180),
                            Color.WHITE
                        )
                    )
                    val radius = view.context.resources.displayMetrics.density * 6f
                    view.background = ViewStateDrawables.buildStateDrawable(
                        DrawableUtil.roundedRipple(
                            fillColor = ColorUtils.setAlphaComponent(palette.titleColor, 28),
                            rippleColor = palette.rippleColor,
                            radius = radius
                        ),
                        DrawableUtil.roundedRipple(
                            fillColor = palette.accentColor,
                            rippleColor = palette.confirmRippleColor,
                            radius = radius
                        ),
                        null
                    )
                }
                true
            }

            else -> false
        }
    }

    private fun createSelectBoxTintList(normalColor: Int, accentColor: Int): ColorStateList {
        return ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_selected),
                intArrayOf(android.R.attr.state_checked),
                intArrayOf(android.R.attr.state_activated),
                intArrayOf()
            ),
            intArrayOf(accentColor, accentColor, accentColor, normalColor)
        )
    }

    protected data class DialogTagPalette(
        val theme: ThemePalette,
        val accentColorOverride: Int,
        val editTextBackground: Drawable
    ) {
        val accentColor: Int get() = accentColorOverride
        val titleColor: Int get() = theme.titleColor
        val messageColor: Int get() = theme.messageColor
        val rippleColor: Int get() = theme.rippleColor
        val dividerColor: Int get() = theme.dividerColor
        val cancelTextColor: Int get() = theme.cancelTextColor
        val cancelBaseColor: Int get() = theme.cancelBaseColor
        val confirmRippleColor: Int get() = theme.confirmRippleColor
        val selectBoxNormalColor: Int = if (titleColor == Color.WHITE) -2171170 else -3355444
    }

    private fun updateWindowLayout(window: Window) {
        val configuration = resources.configuration
        val width = provideWidth(configuration)
        val height = provideHeight(configuration)

        window.setLayout(width, height)

        window.attributes = window.attributes.apply {
            gravity = provideGravity()
            dimAmount = provideDimAmount()

            val softInputMode = provideSoftInputMode()
            if (softInputMode != -1) {
                this.softInputMode = softInputMode
            }

            windowAnimations = provideWindowAnimation()
        }
    }
}
