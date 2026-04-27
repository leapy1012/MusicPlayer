package gd.app.musicplayer.ui.common.dialog

import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import gd.app.musicplayer.core.ui.extension.dpToPx
import gd.app.musicplayer.core.ui.extension.spToPx
import gd.app.musicplayer.core.ui.drawable.ViewStateDrawables

class ActionMessageDialog(
    context: Context,
    config: Config
) : BaseDialog(context, config) {

    class Config : BaseDialog.Config() {

        var forceUppercaseButtons: Boolean = true

        var titleTextColor: Int = 0
        var titleBackgroundColor: Int = 0
        var dividerColor: Int = 0

        var titleTextSizePx: Int = 0
        var messageTextColor: Int = 0
        var messageTextSizePx: Float = 0f
        var actionTextSizePx: Float = 0f

        var horizontalContentPaddingPx: Int = 0
        var customViewVerticalMarginPx: Int = 0

        var messageBackgroundColor: Int = 0
        var buttonDividerColor: Int = 0

        var titleText: String? = null
        var messageText: String? = null
        var customView: View? = null

        var positiveButtonBackground: Drawable? = null
        var negativeButtonBackground: Drawable? = null

        var positiveButtonTextColor: Int = 0
        var negativeButtonTextColor: Int = 0

        var positiveButtonText: String? = null
        var negativeButtonText: String? = null

        var negativeButtonClickListener: DialogInterface.OnClickListener? = null
        var positiveButtonClickListener: DialogInterface.OnClickListener? = null

        companion object {
            fun create(context: Context): Config {
                return Config().apply {
                    buttonDividerColor = 1621336995

                    titleTextSizePx = context.spToPx(20f).toInt()
                    messageTextSizePx = context.spToPx(16f)
                    actionTextSizePx = context.spToPx(18f)

                    horizontalContentPaddingPx = context.dpToPx(12f)

                    messageBackgroundColor = -1
                    messageTextColor = -12895429

                    negativeButtonBackground = ViewStateDrawables.pressedDefaultColorDrawable(0, 436207616)
                    negativeButtonTextColor = -11954701

                    positiveButtonBackground = ViewStateDrawables.pressedDefaultColorDrawable(0, 436207616)
                    positiveButtonTextColor = -11954701

                    titleBackgroundColor = -1
                    dividerColor = -11954701
                    titleTextColor = -11954701

                }
            }

        }
    }

    override fun createContentView(context: Context, config: BaseDialog.Config): View {
        val dialogConfig = config as Config

        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                dialogConfig.contentLeftPaddingPx,
                dialogConfig.contentTopPaddingPx,
                dialogConfig.contentRightPaddingPx,
                dialogConfig.contentBottomPaddingPx
            )

            if (dialogConfig.titleText != null) {
                addTitleView(context, dialogConfig, this)
            }

            if (dialogConfig.messageText != null) {
                addMessageView(context, dialogConfig, this)
            }

            dialogConfig.customView?.let { customContent ->
                if (customContent.parent != null) {
                    (customContent.parent as ViewGroup).removeView(customContent)
                }
                addCustomView(dialogConfig, this)
            }

            if (dialogConfig.positiveButtonText != null || dialogConfig.negativeButtonText != null) {
                addActionButtons(context, dialogConfig, this)
            }
        }
    }

    private fun addTitleView(
        context: Context,
        config: Config,
        root: LinearLayout
    ) {
        val titleView = TextView(context).apply {
            setTextColor(config.titleTextColor)
            setTextSize(0, config.titleTextSizePx.toFloat())
            text = config.titleText
            maxLines = 2
            setBackgroundColor(config.titleBackgroundColor)
            gravity = Gravity.CENTER_VERTICAL

            val horizontalPadding = config.horizontalContentPaddingPx
            setPadding(horizontalPadding, 0, horizontalPadding, 0)
        }

        root.addView(
            titleView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                context.dpToPx(50f)
            )
        )

        val divider = View(context).apply {
            setBackgroundColor(config.dividerColor)
        }

        root.addView(
            divider,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                context.dpToPx(1f)
            )
        )
    }

    private fun addMessageView(
        context: Context,
        config: Config,
        root: LinearLayout
    ) {
        val messageView = TextView(context).apply {
            setTextColor(config.messageTextColor)
            setTextSize(0, config.messageTextSizePx)
            text = config.messageText
            setBackgroundColor(config.messageBackgroundColor)

            val horizontalPadding = config.horizontalContentPaddingPx
            val verticalPadding = config.customViewVerticalMarginPx
            setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
        }

        root.addView(
            messageView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
    }

    private fun addCustomView(
        config: Config,
        root: LinearLayout
    ) {
        val customView = config.customView ?: return

        val layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = config.customViewVerticalMarginPx
            bottomMargin = config.customViewVerticalMarginPx
            leftMargin = config.horizontalContentPaddingPx
            rightMargin = config.horizontalContentPaddingPx
        }

        root.addView(customView, layoutParams)
    }

    private fun addActionButtons(
        context: Context,
        config: Config,
        root: LinearLayout
    ) {
        val topDivider = View(context).apply {
            setBackgroundColor(config.buttonDividerColor)
        }
        root.addView(
            topDivider,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                1
            )
        )

        val buttonRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val buttonLayoutParams = LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.MATCH_PARENT
        ).apply {
            weight = 1f
        }

        config.negativeButtonText?.let { buttonText ->
            val negativeButton = TextView(context).apply {
                setTextColor(config.negativeButtonTextColor)
                setTextSize(0, config.actionTextSizePx)
                text = if (config.forceUppercaseButtons) buttonText.uppercase() else buttonText
                isSingleLine = true
                background = config.negativeButtonBackground
                gravity = Gravity.CENTER
                textAlignment = View.TEXT_ALIGNMENT_CENTER
                setOnClickListener {
                    config.negativeButtonClickListener?.onClick(
                        this@ActionMessageDialog,
                        DialogInterface.BUTTON_NEGATIVE
                    ) ?: dismiss()
                }
            }
            buttonRow.addView(negativeButton, buttonLayoutParams)

            if (config.positiveButtonText != null) {
                val middleDivider = View(context).apply {
                    setBackgroundColor(config.buttonDividerColor)
                }
                buttonRow.addView(
                    middleDivider,
                    LinearLayout.LayoutParams(1, ViewGroup.LayoutParams.MATCH_PARENT)
                )
            }
        }

        config.positiveButtonText?.let { buttonText ->
            val positiveButton = TextView(context).apply {
                setTextColor(config.positiveButtonTextColor)
                setTextSize(0, config.actionTextSizePx)
                text = if (config.forceUppercaseButtons) buttonText.uppercase() else buttonText
                isSingleLine = true
                background = config.positiveButtonBackground
                gravity = Gravity.CENTER
                textAlignment = View.TEXT_ALIGNMENT_CENTER
                setOnClickListener {
                    config.positiveButtonClickListener?.onClick(
                        this@ActionMessageDialog,
                        DialogInterface.BUTTON_POSITIVE
                    ) ?: dismiss()
                }
            }
            buttonRow.addView(positiveButton, buttonLayoutParams)
        }

        root.addView(
            buttonRow,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                context.dpToPx(46f)
            )
        )
    }

    companion object {
        fun show(activity: Activity, config: Config) {
            if (activity.isFinishing) return

            val existingDialog = DialogRegistry
                .let { registry ->
                    // Reuse cache through BaseDialog companion API
                    null
                }

            val dialog = existingDialog ?: ActionMessageDialog(activity, config)
            dialog.show()
        }
    }
}