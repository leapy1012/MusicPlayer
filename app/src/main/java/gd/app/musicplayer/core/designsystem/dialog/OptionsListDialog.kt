package gd.app.musicplayer.core.designsystem.dialog

import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.spToPx
import kotlin.math.roundToInt
import androidx.core.graphics.drawable.toDrawable
import gd.app.musicplayer.core.common.extension.isLandscape
import gd.app.musicplayer.core.common.extension.screenHeight
import gd.app.musicplayer.core.designsystem.drawable.disabledSelectedDefaultColors
import gd.app.musicplayer.core.designsystem.drawable.pressedDefaultColorDrawable
import gd.app.musicplayer.core.designsystem.drawable.selectedDefaultColors


class OptionsListDialog(
    context: Context,
    config: Config
) : BaseDialog(context, config) {

    class Config : BaseDialog.Config() {
        var titleTextColor: Int = 0
        var titleTextSizePx: Int = 0
        var buttonTextSizePx: Float = 0f

        var titleText: String? = null
        var items: List<String>? = null
        var adapter: BaseAdapter? = null

        var onItemClickListener: AdapterView.OnItemClickListener? = null
        var onItemLongClickListener: AdapterView.OnItemLongClickListener? = null

        var itemTextColor: Int = 0
        var itemTextSizePx: Int = 0
        var itemMinHeightPx: Int = 0
        var singleLineItems: Boolean = true
        var itemDividerColor: Int = 0

        var positiveButtonBackground: Drawable? = null
        var negativeButtonBackground: Drawable? = null
        var positiveButtonTextColor: Int = 0
        var negativeButtonTextColor: Int = 0

        var positiveButtonText: String? = null
        var negativeButtonText: String? = null
        var positiveButtonClickListener: DialogInterface.OnClickListener? = null
        var negativeButtonClickListener: DialogInterface.OnClickListener? = null

        var selectedItemIndex: Int = -1
        var selectedItemTextColor: Int = 0

        var itemTypeface: Typeface? = null
        var titleTypeface: Typeface? = null

        var itemIconRes: Int = 0
        var itemIconTintList: ColorStateList? = null
        var itemIconPlacement: Int = 0
        var itemLayoutRes: Int = R.layout.common_item_list_dialog

        var listHeightPx: Int = 0
        var forceUppercaseButtons: Boolean = true

        init {
            dialogLayoutRes = R.layout.common_material_list_dialog_layout
        }

        companion object {
            fun create(context: Context, items: List<String>): Config {
                return Config().apply {
                    this.items = items

                    titleTextSizePx = context.spToPx(20f).roundToInt()
                    itemTextSizePx = context.spToPx(16f).roundToInt()
                    buttonTextSizePx = context.spToPx(14f)

                    titleTypeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                    itemTypeface = Typeface.DEFAULT_BOLD

                    itemDividerColor = 0
                    itemTextColor = -10066330
                    selectedItemTextColor = -15032591
                    singleLineItems = true

                    negativeButtonBackground = pressedDefaultColorDrawable(0, 437952241)
                    negativeButtonTextColor = -15032591

                    positiveButtonBackground = pressedDefaultColorDrawable(0, 437952241)
                    positiveButtonTextColor = -15032591

                    titleTextColor = -16777216
                }
            }
        }
    }

    private class DefaultItemAdapter(
        private val context: Context,
        private val config: Config
    ) : BaseAdapter() {

        private val pressedOverlayColor: Int = run {
            var baseColor = config.selectedItemTextColor
            if (baseColor == 0) baseColor = config.positiveButtonTextColor
            if (baseColor == 0) baseColor = config.negativeButtonTextColor
            if (baseColor == 0) baseColor = config.titleTextColor
            if (baseColor == 0) baseColor = Color.BLACK
            Color.argb(26, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor))
        }

        override fun getCount(): Int = config.items?.size ?: 0

        override fun getItem(position: Int): String =
            config.items?.get(position).orEmpty()

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val itemView = convertView ?: LayoutInflater.from(context)
                .inflate(config.itemLayoutRes, null)

            val holder = (itemView.tag as? ItemViewHolder) ?: ItemViewHolder(itemView, config).also {
                bindStaticItemStyle(it, pressedOverlayColor)
                itemView.tag = it
            }

            bindItem(holder, position)
            return itemView
        }

        private fun bindItem(holder: ItemViewHolder, position: Int) {
            val isSelected = position == config.selectedItemIndex

            holder.leftIcon.isSelected = isSelected
            holder.rightIcon.isSelected = isSelected
            holder.titleView.isSelected = isSelected
            holder.titleView.text = getItem(position)
        }

        private fun bindStaticItemStyle(holder: ItemViewHolder, pressedOverlayColor: Int) {
            if (config.itemIconRes != 0) {
                val iconView =
                    if (config.itemIconPlacement == 0) holder.leftIcon else holder.rightIcon

                val tintList = config.itemIconTintList ?: run {
                    val inactiveColor = ColorUtils.setAlphaComponent(config.itemTextColor, 128)
                    disabledSelectedDefaultColors(inactiveColor, config.selectedItemTextColor, inactiveColor)
                }

                iconView.setImageResource(config.itemIconRes)
                iconView.imageTintList = tintList
                iconView.visibility = View.VISIBLE
            }

            if (config.itemMinHeightPx > 0) {
                holder.root.minimumHeight = config.itemMinHeightPx
            }

            holder.titleView.setTextColor(
                selectedDefaultColors(config.itemTextColor, config.selectedItemTextColor)
            )
            holder.titleView.setTextSize(0, config.itemTextSizePx.toFloat())

            holder.root.background = pressedDefaultColorDrawable(Color.TRANSPARENT, pressedOverlayColor)
        }

    }

    private class ItemViewHolder(
        val root: View,
        config: Config
    ) {
        val leftIcon: ImageView = root.findViewById(R.id.common_list_item_image_left)
        val rightIcon: ImageView = root.findViewById(R.id.common_list_item_image_right)
        val titleView: TextView = root.findViewById(R.id.common_list_item_text)

        init {
            titleView.isSingleLine = config.singleLineItems
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

            if (dialogConfig.items != null || dialogConfig.adapter != null) {
                bindList(context, this, dialogConfig)
            }

            if (
                dialogConfig.positiveButtonText != null ||
                dialogConfig.negativeButtonText != null
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
            config.itemTypeface?.let(button::setTypeface)
            button.background = config.negativeButtonBackground
            button.setOnClickListener {
                config.negativeButtonClickListener?.onClick(
                    this,
                    BUTTON_NEGATIVE
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
            config.itemTypeface?.let(button::setTypeface)
            button.background = config.positiveButtonBackground
            button.setOnClickListener {
                config.positiveButtonClickListener?.onClick(
                    this,
                    BUTTON_POSITIVE
                ) ?: dismiss()
            }
            button.visibility = View.VISIBLE
            buttonContainer.visibility = View.VISIBLE
        }
    }

    private fun bindList(context: Context, rootView: View, config: Config) {
        val listView = rootView.findViewById<ListView>(R.id.common_dialog_list_view)
        listView.visibility = View.VISIBLE

        if (config.itemDividerColor == 0) {
            listView.divider = null
            listView.dividerHeight = 0
        } else {
            listView.divider = config.itemDividerColor.toDrawable()
            listView.dividerHeight = 1
        }

        if (config.adapter == null) {
            config.adapter = DefaultItemAdapter(context, config)
        }

        listView.adapter = config.adapter as ListAdapter
        listView.onItemClickListener = config.onItemClickListener
        listView.onItemLongClickListener = config.onItemLongClickListener

        if (config.selectedItemIndex in 0 until (config.adapter?.count ?: 0)) {
            listView.setSelection(config.selectedItemIndex)
        }

        val targetHeight = if (config.listHeightPx != 0) {
            config.listHeightPx
        } else {
            calculateDefaultListHeight(context, config)
        }

        (listView.layoutParams as? LinearLayout.LayoutParams)?.let { params ->
            params.height = targetHeight
            listView.layoutParams = params
        }

        listView.visibility = View.VISIBLE
    }

    private fun calculateDefaultListHeight(context: Context, config: Config): Int {
        val adapter = config.adapter ?: return ViewGroup.LayoutParams.WRAP_CONTENT
        val sampleView = adapter.getView(0, null, null)
        sampleView.measure(0, 0)

        val totalMeasuredHeight = sampleView.measuredHeight * maxOf(1, adapter.count)
        val maxAllowedHeight = (context.screenHeight * 2) / (if (context.isLandscape()) 4 else 3)

        return if (totalMeasuredHeight < maxAllowedHeight) {
            ViewGroup.LayoutParams.WRAP_CONTENT
        } else {
            maxAllowedHeight
        }
    }

    private fun bindTitle(rootView: View, config: Config) {
        val titleView = rootView.findViewById<TextView>(R.id.common_dialog_title)
        titleView.visibility = View.VISIBLE
        titleView.setTextColor(config.titleTextColor)
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_PX, config.titleTextSizePx.toFloat())
        titleView.text = config.titleText
        config.titleTypeface?.let(titleView::setTypeface)
    }

    companion object {
        fun show(activity: Activity, config: Config) {
            if (activity.isFinishing) return
            val dialog = OptionsListDialog(activity, config)
            dialog.show()
        }
    }
}
