package gd.app.musicplayer.core.designsystem.dialog

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.coui.appcompat.checkbox.COUICheckBox
import com.coui.appcompat.dialog.COUIAlertDialogBuilder
import gd.app.musicplayer.R
import gd.app.musicplayer.databinding.DialogConfirmExtraBinding

/**
 * Shared COUI confirm dialog: title + message + Cancel/OK, optional "delete source file" checkbox.
 */
open class CouiConfirmDialogFragment : DialogFragment() {

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
        }
        return dialog
    }

    protected fun setPositiveEnabled(enabled: Boolean) {
        (dialog as? AlertDialog)?.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = enabled
        (dialog as? AlertDialog)?.getButton(AlertDialog.BUTTON_NEGATIVE)?.isEnabled = enabled
    }
}
