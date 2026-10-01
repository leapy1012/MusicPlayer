package gd.app.musicplayer.core.designsystem.dialog

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.coui.appcompat.checkbox.COUICheckBox
import com.coui.appcompat.dialog.COUIAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.databinding.DialogConfirmExtraBinding
import gd.app.musicplayer.ui.theme.ThemeEngine
import javax.inject.Inject

/**
 * Shared COUI confirm dialog: title + message + Cancel/OK, optional "delete source file" checkbox.
 */
@AndroidEntryPoint
open class CouiConfirmDialogFragment : DialogFragment() {

    @Inject
    lateinit var themeEngine: ThemeEngine

    protected open fun provideTitle(): CharSequence = ""
    protected open fun provideMessage(): CharSequence = ""
    protected open fun providePositiveText(): CharSequence = getString(R.string.ok)
    protected open fun provideNegativeText(): CharSequence = getString(R.string.cancel)
    protected open fun showExtraCheckbox(): Boolean = false
    protected open fun extraCheckboxCheckedByDefault(): Boolean = false
    protected open fun onPositiveClicked(extraChecked: Boolean) = Unit

    private var extraCheckbox: COUICheckBox? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = COUIAlertDialogBuilder(
            requireContext(),
            com.coui.appcompat.R.style.COUIAlertDialog_Center
        )
            .setTitle(provideTitle())
            .setMessage(provideMessage())
            .setNegativeButton(provideNegativeText(), null)
            .setPositiveButton(providePositiveText(), null)

        if (showExtraCheckbox()) {
            val extra = DialogConfirmExtraBinding.inflate(LayoutInflater.from(requireContext()))
            extraCheckbox = extra.dialogCommenDeleteSelect
            extra.dialogCommenDeleteSelect.isChecked = extraCheckboxCheckedByDefault()
            extra.dialogCommenExtraLayout.setOnClickListener {
                extra.dialogCommenDeleteSelect.toggle()
            }
            builder.setView(extra.root)
        }

        val dialog = builder.create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                onPositiveClicked(extraCheckbox?.isChecked == true)
            }
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setOnClickListener {
                dismiss()
            }
            builder.updateViewAfterShown()
            CouiAlertDialogSurface.apply(
                dialog,
                themeEngine.currentTheme().getDialogSurfaceDrawable(requireContext())
            )
        }
        return dialog
    }

    protected fun setPositiveEnabled(enabled: Boolean) {
        (dialog as? AlertDialog)?.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = enabled
        (dialog as? AlertDialog)?.getButton(AlertDialog.BUTTON_NEGATIVE)?.isEnabled = enabled
    }
}
