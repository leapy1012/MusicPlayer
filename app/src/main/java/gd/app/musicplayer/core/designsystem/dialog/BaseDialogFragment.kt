package gd.app.musicplayer.core.designsystem.dialog

import android.content.DialogInterface
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.core.graphics.drawable.toDrawable
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.getMinScreenSize
import gd.app.musicplayer.core.designsystem.theme.accentColor
import gd.app.musicplayer.core.designsystem.theme.ThemeObserver
import gd.app.musicplayer.core.designsystem.theme.ThemePalette
import gd.app.musicplayer.core.designsystem.theme.ThemeRegistry
import gd.app.musicplayer.ui.common.base.BaseActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import gd.app.musicplayer.ui.theme.ThemeEngine
import javax.inject.Inject

@AndroidEntryPoint
open class BaseDialogFragment : DialogFragment(), ThemeObserver {

    @Inject
    lateinit var themeEngine: ThemeEngine

    @Inject
    lateinit var themeRegistry: ThemeRegistry
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

    protected open fun provideBackgroundDrawable(): Drawable {
        return themeEngine.currentTheme()
            .getDialogSurfaceDrawable(requireContext())
    }

    protected open fun transformRootView(view: View): View = view

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
        super.onViewCreated(view, savedInstanceState)
        applyThemeTo(transformRootView(view))
    }

    override fun onStart() {
        super.onStart()
        themeRegistry.registerObserver(this)

        val dialog = dialog ?: return
        val window = dialog.window ?: return

        updateWindowLayout(window)

        window.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        dialog.setCanceledOnTouchOutside(shouldAllowOutsideTouchCancel())

        flushPendingUiActions()
    }

    override fun onResume() {
        super.onResume()
        applyThemeTo(view)
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

    override fun onStop() {
        themeRegistry.unregisterObserver(this)
        super.onStop()
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

    override fun onThemeChanged(palette: ThemePalette?) {
        applyThemeTo(view)
    }

    protected fun applyThemeTo(root: View?) {
        val target = root ?: return
        themeEngine.apply(target)
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
        applyThemeTo(rootView)
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
