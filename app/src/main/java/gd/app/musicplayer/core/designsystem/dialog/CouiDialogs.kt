package gd.app.musicplayer.core.designsystem.dialog

import android.app.Activity
import android.content.DialogInterface
import androidx.appcompat.app.AlertDialog
import com.coui.appcompat.dialog.COUIAlertDialogBuilder
import com.coui.appcompat.dialog.adapter.ChoiceListAdapter
import com.coui.appcompat.R as CouiR

/**
 * Bridges legacy [MessageDialog.Config] / [OptionsListDialog.Config] onto
 * [COUIAlertDialogBuilder] so call sites keep working while chrome is fully COUI.
 */
internal object CouiDialogs {

    fun showMessage(activity: Activity, config: MessageDialog.Config) {
        if (activity.isFinishing) return

        val style = when {
            config.customView != null -> CouiR.style.COUIAlertDialog_BottomAssignment
            else -> CouiR.style.COUIAlertDialog_Center
        }

        val builder = COUIAlertDialogBuilder(activity, style)
        config.titleText?.takeIf { it.isNotBlank() }?.let(builder::setTitle)
        config.messageText?.let(builder::setMessage)
        config.customView?.let { view ->
            (view.parent as? android.view.ViewGroup)?.removeView(view)
            enableQuickDeleteOnEditTexts(view)
            builder.setView(view)
        }

        // Listeners registered in onShow so we can match MessageDialog: only auto-dismiss
        // when the caller did not supply a click listener.
        if (!config.negativeButtonText.isNullOrBlank()) {
            builder.setNegativeButton(config.negativeButtonText, null)
        }
        if (!config.neutralButtonText.isNullOrBlank()) {
            builder.setNeutralButton(config.neutralButtonText, null)
        }
        if (!config.positiveButtonText.isNullOrBlank()) {
            builder.setPositiveButton(config.positiveButtonText, null)
        }

        builder.setCancelable(config.cancelable)
        val dialog = builder.create()
        dialog.setCanceledOnTouchOutside(config.cancelable)
        DialogRegistry.registerAlert(activity, dialog)
        dialog.setOnDismissListener {
            DialogRegistry.unregisterAlert(activity, dialog)
            config.onDismissListener?.onDismiss(dialog)
        }
        dialog.setOnShowListener {
            wireButton(
                dialog,
                AlertDialog.BUTTON_NEGATIVE,
                config.negativeButtonText,
                config.negativeButtonClickListener
            )
            wireButton(
                dialog,
                AlertDialog.BUTTON_NEUTRAL,
                config.neutralButtonText,
                config.neutralButtonClickListener
            )
            wireButton(
                dialog,
                AlertDialog.BUTTON_POSITIVE,
                config.positiveButtonText,
                config.positiveButtonClickListener
            )
            builder.updateViewAfterShown()
        }
        dialog.show()
    }

    fun showOptionsList(activity: Activity, config: OptionsListDialog.Config) {
        if (activity.isFinishing) return

        val items = config.items
        val builder = COUIAlertDialogBuilder(activity, CouiR.style.COUIAlertDialog_List)
        config.titleText?.takeIf { it.isNotBlank() }?.let(builder::setTitle)

        val itemClick = config.onItemClickListener
        when {
            config.adapter != null -> {
                builder.setAdapter(config.adapter) { dialog, which ->
                    itemClick?.onItemClick(null, null, which, which.toLong())
                    // Call sites typically dismiss via DialogRegistry / BaseDialog.dismissAll.
                    if (itemClick == null) dialog.dismiss()
                }
            }

            items != null && (config.selectedItemIndex >= 0 || config.itemIconRes != 0) -> {
                val checked = BooleanArray(items.size) { it == config.selectedItemIndex }
                val adapter = ChoiceListAdapter(
                    activity,
                    CouiR.layout.coui_select_dialog_singlechoice,
                    items.toTypedArray(),
                    null,
                    checked,
                    false
                )
                builder.setAdapter(adapter) { dialog, which ->
                    itemClick?.onItemClick(null, null, which, which.toLong())
                    dialog.dismiss()
                }
            }

            items != null -> {
                builder.setItems(items.toTypedArray()) { dialog, which ->
                    itemClick?.onItemClick(null, null, which, which.toLong())
                    dialog.dismiss()
                }
            }
        }

        if (!config.negativeButtonText.isNullOrBlank()) {
            builder.setNegativeButton(config.negativeButtonText, null)
        }
        if (!config.positiveButtonText.isNullOrBlank()) {
            builder.setPositiveButton(config.positiveButtonText, null)
        }

        builder.setCancelable(config.cancelable)
        val dialog = builder.create()
        dialog.setCanceledOnTouchOutside(config.cancelable)
        DialogRegistry.registerAlert(activity, dialog)
        dialog.setOnDismissListener {
            DialogRegistry.unregisterAlert(activity, dialog)
        }
        dialog.setOnShowListener {
            wireButton(
                dialog,
                AlertDialog.BUTTON_NEGATIVE,
                config.negativeButtonText,
                config.negativeButtonClickListener
            )
            wireButton(
                dialog,
                AlertDialog.BUTTON_POSITIVE,
                config.positiveButtonText,
                config.positiveButtonClickListener
            )
            builder.updateViewAfterShown()
        }
        dialog.show()
    }

    private fun wireButton(
        dialog: AlertDialog,
        which: Int,
        text: String?,
        listener: DialogInterface.OnClickListener?
    ) {
        if (text.isNullOrBlank()) return
        dialog.getButton(which)?.setOnClickListener {
            if (listener != null) {
                listener.onClick(dialog, which)
            } else {
                dialog.dismiss()
            }
        }
    }

    private fun enableQuickDeleteOnEditTexts(root: android.view.View) {
        when (root) {
            is com.coui.appcompat.edittext.COUIEditText -> root.setFastDeletable(true)
            is android.view.ViewGroup -> {
                for (i in 0 until root.childCount) {
                    enableQuickDeleteOnEditTexts(root.getChildAt(i))
                }
            }
        }
    }
}
