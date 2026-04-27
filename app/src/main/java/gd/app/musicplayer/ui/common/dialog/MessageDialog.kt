package gd.app.musicplayer.ui.common.dialog

import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import gd.app.musicplayer.R
import gd.app.musicplayer.core.ui.extension.dpToPx
import gd.app.musicplayer.core.ui.extension.spToPx
import gd.app.musicplayer.core.ui.drawable.ViewStateDrawables

class MessageDialog(
    context: Context,
    config: Config
) : BaseDialog(context, config) {

    class Config : BaseDialog.Config() {
        var titleTextColor: Int = 0
        var titleTextSizePx: Int = 0

        var messageTextColor: Int = 0
        var messageTextSizePx: Float = 0f
        var messageLineSpacingMultiplier: Float = 1.2f
        var messageLineSpacingExtraPx: Float = 0f

        var buttonTextSizePx: Float = 0f

        var titleText: String? = null
        var messageText: String? = null
        var customView: View? = null

        var customViewLeftMarginPx: Int = 0
        var customViewRightMarginPx: Int = 0
        var customViewTopMarginPx: Int = 0
        var customViewBottomMarginPx: Int = 0

        var positiveButtonBackground: Drawable? = null
        var negativeButtonBackground: Drawable? = null
        var neutralButtonBackground: Drawable? = null

        var positiveButtonTextColor: Int = 0
        var negativeButtonTextColor: Int = 0
        var neutralButtonTextColor: Int = 0

        var positiveButtonText: String? = null
        var negativeButtonText: String? = null
        var neutralButtonText: String? = null

        var positiveButtonClickListener: DialogInterface.OnClickListener? = null
        var negativeButtonClickListener: DialogInterface.OnClickListener? = null
        var neutralButtonClickListener: DialogInterface.OnClickListener? = null

        var buttonTypeface: Typeface? = null
        var titleTypeface: Typeface? = null

        var forceUppercaseButtons: Boolean = true

        init {
            dialogLayoutRes = R.layout.music_material_dialog_layout
        }

        companion object {
            fun create(context: Context): Config {
                return Config().apply {
                    contentTopPaddingPx = context.dpToPx(24f)

                    titleTextSizePx = context.spToPx(20f).toInt()
                    messageTextSizePx = context.spToPx(16f)
                    buttonTextSizePx = context.spToPx(14f)

                    titleTypeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                    buttonTypeface = Typeface.DEFAULT_BOLD

                    messageTextColor = -10066330

                    negativeButtonBackground = ViewStateDrawables.pressedDefaultColorDrawable(0, 437952241)
                    negativeButtonTextColor = -15032591

                    neutralButtonBackground = ViewStateDrawables.pressedDefaultColorDrawable(0, 437952241)
                    neutralButtonTextColor = -15032591

                    positiveButtonBackground = ViewStateDrawables.pressedDefaultColorDrawable(0, 437952241)
                    positiveButtonTextColor = -15032591

                    titleTextColor = -16777216

                    customViewTopMarginPx = 0
                    val horizontalMargin = context.dpToPx(24f)
                    customViewLeftMarginPx = horizontalMargin
                    customViewRightMarginPx = horizontalMargin
                    customViewBottomMarginPx = horizontalMargin

                }
            }
        }
    }

    override fun createContentView(context: Context, config: BaseDialog.Config): View {
        val dialogConfig = config as Config
        val layoutRes = dialogConfig.layoutProvider.getLayoutRes(dialogConfig)

        if (layoutRes == 0) {
            return LinearLayout(context)
        }

        return View.inflate(context, layoutRes, null).apply {
            setPadding(
                dialogConfig.contentLeftPaddingPx,
                dialogConfig.contentTopPaddingPx,
                dialogConfig.contentRightPaddingPx,
                dialogConfig.contentBottomPaddingPx
            )

            if (dialogConfig.titleText != null) {
                bindTitle(this, dialogConfig)
            }

            if (dialogConfig.messageText != null) {
                bindMessage(this, dialogConfig)
            }

            dialogConfig.customView?.let { customView ->
                if (customView.parent != null) {
                    (customView.parent as ViewGroup).removeView(customView)
                }
                bindCustomView(this, dialogConfig)
            }

            if (
                dialogConfig.positiveButtonText != null ||
                dialogConfig.negativeButtonText != null ||
                dialogConfig.neutralButtonText != null
            ) {
                bindButtons(this, dialogConfig)
            }
        }
    }

    private fun bindButtons(rootView: View, config: Config) {
        val buttonContainer = rootView.findViewById<View>(R.id.common_dialog_button_container)

        config.negativeButtonText?.let { text ->
            val button = buttonContainer.findViewById<TextView>(R.id.common_dialog_negative)
            button.setTextColor(config.negativeButtonTextColor)
            button.setTextSize(0, config.buttonTextSizePx)
            button.text = if (config.forceUppercaseButtons) text.uppercase() else text
            config.buttonTypeface?.let(button::setTypeface)
            button.background = config.negativeButtonBackground
            button.setOnClickListener {
                config.negativeButtonClickListener?.onClick(
                    this,
                    DialogInterface.BUTTON_NEGATIVE
                ) ?: dismiss()
            }
            button.visibility = View.VISIBLE
            buttonContainer.visibility = View.VISIBLE
        }

        config.neutralButtonText?.let { text ->
            val button = buttonContainer.findViewById<TextView>(R.id.common_dialog_neutral)
            button.setTextColor(config.neutralButtonTextColor)
            button.setTextSize(0, config.buttonTextSizePx)
            button.text = if (config.forceUppercaseButtons) text.uppercase() else text
            config.buttonTypeface?.let(button::setTypeface)
            button.background = config.neutralButtonBackground
            button.setOnClickListener {
                config.neutralButtonClickListener?.onClick(
                    this,
                    DialogInterface.BUTTON_NEUTRAL
                ) ?: dismiss()
            }
            button.visibility = View.VISIBLE
            buttonContainer.visibility = View.VISIBLE
        }

        config.positiveButtonText?.let { text ->
            val button = buttonContainer.findViewById<TextView>(R.id.common_dialog_positive)
            button.setTextColor(config.positiveButtonTextColor)
            button.setTextSize(0, config.buttonTextSizePx)
            button.text = if (config.forceUppercaseButtons) text.uppercase() else text
            config.buttonTypeface?.let(button::setTypeface)
            button.background = config.positiveButtonBackground
            button.setOnClickListener {
                config.positiveButtonClickListener?.onClick(
                    this,
                    DialogInterface.BUTTON_POSITIVE
                ) ?: dismiss()
            }
            button.visibility = View.VISIBLE
            buttonContainer.visibility = View.VISIBLE
        }
    }

    private fun bindCustomView(rootView: View, config: Config) {
        val container = rootView.findViewById<FrameLayout>(R.id.common_dialog_custom_container)
        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = config.customViewTopMarginPx
            leftMargin = config.customViewLeftMarginPx
            rightMargin = config.customViewRightMarginPx
            bottomMargin = config.customViewBottomMarginPx
        }

        container.addView(config.customView, params)
        container.visibility = View.VISIBLE
    }

    private fun bindMessage(rootView: View, config: Config) {
        val messageView = rootView.findViewById<TextView>(R.id.common_dialog_msg)
        messageView.visibility = View.VISIBLE
        messageView.setTextColor(config.messageTextColor)
        messageView.setTextSize(0, config.messageTextSizePx)
        messageView.text = config.messageText
        messageView.setLineSpacing(
            config.messageLineSpacingExtraPx,
            config.messageLineSpacingMultiplier
        )
    }

    private fun bindTitle(rootView: View, config: Config) {
        val titleView = rootView.findViewById<TextView>(R.id.common_dialog_title)
        titleView.visibility = View.VISIBLE
        titleView.setTextColor(config.titleTextColor)
        titleView.setTextSize(0, config.titleTextSizePx.toFloat())
        titleView.text = config.titleText
        config.titleTypeface?.let(titleView::setTypeface)
    }

    companion object {
        fun show(activity: Activity, config: Config) {
            if (activity.isFinishing) return
            val dialog = MessageDialog(activity, config)
            dialog.show()
        }
    }
}