package gd.app.musicplayer.ui.common.dialog

import android.content.DialogInterface
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.drawable.toDrawable
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import gd.app.musicplayer.R
import gd.app.musicplayer.core.ui.extension.getMinScreenSize
import gd.app.musicplayer.ui.common.base.BaseActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

abstract class BaseDialogFragment : DialogFragment() {

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

    protected abstract fun provideBackgroundDrawable(): Drawable

    protected open fun transformRootView(view: View): View = view

    protected open fun provideDimAmount(): Float = 0.35f

    protected open fun provideGravity(): Int = android.view.Gravity.CENTER

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
        setStyle(STYLE_NO_TITLE, android.R.style.Theme_Material_Light_Dialog_Alert)
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

    private fun updateWindowLayout(window: android.view.Window) {
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