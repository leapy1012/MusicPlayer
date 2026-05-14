package gd.app.musicplayer.core.designsystem.dialog

import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.view.View

fun Context.createMessageDialogConfig(
    title: CharSequence? = null,
    message: CharSequence? = null,
    customView: View? = null,
    positiveText: CharSequence? = null,
    negativeText: CharSequence? = null,
    neutralText: CharSequence? = null,
    positiveClickListener: DialogInterface.OnClickListener? = null,
    negativeClickListener: DialogInterface.OnClickListener? = null,
    neutralClickListener: DialogInterface.OnClickListener? = null,
): MessageDialog.Config {
    return MessageDialog.Config.create(this).apply {
        titleText = title?.toString()
        messageText = message?.toString()
        this.customView = customView
        positiveButtonText = positiveText?.toString()
        negativeButtonText = negativeText?.toString()
        neutralButtonText = neutralText?.toString()
        positiveButtonClickListener = positiveClickListener
        negativeButtonClickListener = negativeClickListener
        neutralButtonClickListener = neutralClickListener
    }
}

fun Activity.showMessageDialog(config: MessageDialog.Config) {
    MessageDialog.show(this, config)
}
