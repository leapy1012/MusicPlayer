package gd.app.musicplayer.ui.common.base

import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.ImageViewCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.getMaxScreenSize
import gd.app.musicplayer.core.common.extension.getMinScreenSize
import gd.app.musicplayer.core.designsystem.drawable.rectRippleDrawable
import gd.app.musicplayer.core.designsystem.theme.DialogSurfaceColors
import gd.app.musicplayer.core.designsystem.theme.ThemePalette
import gd.app.musicplayer.core.designsystem.theme.dialogPressedOverlayColor

/**
 * Grid bottom-sheet menu (album/track overflow, etc.).
 *
 * Layout XML hardcodes `#ffffff` for titles/labels (pictured/dark default). Theme-tag
 * walks do not retint those views, so light dialogs painted a white plate and left
 * white-on-white content. Colors are applied explicitly from [dialogContentColor].
 */
abstract class BaseBottomGridMenuDialog : BaseBottomRecyclerMenuDialog() {

    protected var titleView: TextView? = null
        private set

    private var titleIconView: ImageView? = null
    private var titleIconView2: ImageView? = null
    private var menuAdapter: MenuAdapter? = null

    private inner class MenuAdapter(
        private val inflater: LayoutInflater,
        initialItems: List<MenuItem>
    ) : RecyclerView.Adapter<MenuViewHolder>() {

        private val items = initialItems.toMutableList()

        override fun getItemCount(): Int = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MenuViewHolder {
            val itemView = inflater.inflate(
                R.layout.dialog_base_bottom_grid_item,
                parent,
                false
            )
            return MenuViewHolder(itemView)
        }

        override fun onBindViewHolder(holder: MenuViewHolder, position: Int) {
            holder.bind(items[position])
        }

        fun notifyItemUpdated(item: MenuItem) {
            val index = items.indexOfFirst { it.id == item.id }
            if (index >= 0) {
                items[index] = item
                notifyItemChanged(index, PAYLOAD_UPDATE_ITEM)
            }
        }

        fun updateItems(newItems: List<MenuItem>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        fun retintVisible() {
            notifyDataSetChanged()
        }
    }

    private inner class MenuViewHolder(
        itemView: View
    ) : RecyclerView.ViewHolder(itemView), View.OnClickListener {

        private val iconView: ImageView = itemView.findViewById(R.id.menu_item_image)
        private val textView: TextView = itemView.findViewById(R.id.menu_item_text)

        private var boundItem: MenuItem? = null

        init {
            itemView.setOnClickListener(this)
        }

        fun bind(item: MenuItem) {
            boundItem = item
            iconView.setImageResource(item.iconResId)
            textView.text = item.label ?: itemView.context.getString(item.id)
            applyMenuItemChrome(iconView, textView, itemView)
        }

        override fun onClick(view: View) {
            boundItem?.let(::onMenuItemClicked)
        }
    }

    override fun onCreateRecyclerArea(
        inflater: LayoutInflater,
        recyclerView: RecyclerView
    ) {
        val items = provideMenuItems()
        val spanCount = resolveSpanCount(items.size)

        recyclerView.layoutManager = GridLayoutManager(requireContext(), spanCount)
        recyclerView.adapter = MenuAdapter(inflater, items).also {
            menuAdapter = it
        }
    }

    override fun onCreateTitleArea(
        inflater: LayoutInflater,
        container: LinearLayout
    ) {
        super.onCreateTitleArea(inflater, container)

        inflater.inflate(
            R.layout.dialog_base_bottom_grid_title,
            container,
            true
        )

        val titleTextView = container.findViewById<TextView>(R.id.bottom_menu_title)
        val titleIcon = container.findViewById<ImageView>(R.id.bottom_menu_title_icon)
        val titleIcon2 = container.findViewById<ImageView>(R.id.bottom_menu_title_icon_2)

        titleTextView.maxWidth = calculateTitleMaxWidth(
            requireContext().resources.configuration
        )

        titleView = titleTextView
        titleIconView = titleIcon
        titleIconView2 = titleIcon2
        onBindTitleArea(container, titleTextView, titleIcon)
        // Subclasses (e.g. CurrentTrackOptionsDialog) may replace the title layout;
        // rebind refs so chrome tints the live views, not the detached originals.
        titleView = container.findViewById(R.id.bottom_menu_title)
        titleIconView = container.findViewById(R.id.bottom_menu_title_icon)
        titleIconView2 = container.findViewById(R.id.bottom_menu_title_icon_2)
        titleView?.maxWidth = calculateTitleMaxWidth(
            requireContext().resources.configuration
        )
        applyTitleChrome()
    }

    override fun onThemeChanged(palette: ThemePalette?) {
        super.onThemeChanged(palette)
        applyTitleChrome()
        menuAdapter?.retintVisible()
    }

    override fun onStart() {
        super.onStart()
        applyTitleChrome()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        titleView?.maxWidth = calculateTitleMaxWidth(requireContext().resources.configuration)
    }

    protected fun notifyMenuItemUpdated(item: MenuItem) {
        onMenuItemBound(item)
        menuAdapter?.notifyItemUpdated(item)
    }

    protected fun refreshMenuItems() {
        menuAdapter?.updateItems(provideMenuItems())
    }

    private fun applyMenuItemChrome(iconView: ImageView, textView: TextView, itemView: View) {
        val palette = themeEngine.currentTheme()
        val color = dialogContentColor(palette)
        applyDialogItemStyle(iconView, color)
        applyDialogItemStyle(textView, color)
        applyDialogItemBackground(itemView, palette.dialogPressedOverlayColor)
    }

    private fun applyTitleChrome() {
        val palette = themeEngine.currentTheme()
        val color = dialogContentColor(palette)
        titleView?.let { applyDialogItemStyle(it, color) }
        titleIconView?.let { applyDialogItemStyle(it, color) }
        titleIconView2?.let { applyDialogItemStyle(it, color) }
    }

    /**
     * Light dialog plate → dark content; pictured/dark plate → white content.
     * Do not trust activity COUI tokens alone (Dark chrome overlay can leave LabelPrimary white).
     */
    private fun dialogContentColor(palette: ThemePalette): Int {
        return DialogSurfaceColors.contentColor(palette)
    }

    protected open fun calculateTitleMaxWidth(configuration: Configuration): Int {
        val activity = requireActivity() as BaseActivity
        val baseWidth = if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            activity.getMaxScreenSize()
        } else {
            activity.getMinScreenSize()
        }
        return (baseWidth * 0.68f).toInt()
    }

    protected open fun onBindTitleArea(
        container: View,
        titleView: TextView,
        titleIconView: ImageView
    ) = Unit

    protected fun applyDialogItemStyle(
        themedView: View,
        itemContentColor: Int,
        pressedColor: Int? = null
    ): Boolean {
        return when (themedView) {
            is TextView -> {
                themedView.setTextColor(itemContentColor)
                true
            }

            is ImageView -> {
                ImageViewCompat.setImageTintList(
                    themedView,
                    ColorStateList.valueOf(itemContentColor)
                )
                true
            }

            else -> {
                if (pressedColor != null) {
                    themedView.background = rectRippleDrawable(Color.TRANSPARENT, pressedColor)
                    true
                } else {
                    false
                }
            }
        }
    }

    private fun resolveSpanCount(itemCount: Int): Int {
        return if (itemCount != 4 && itemCount <= 6) 3 else 4
    }

    private companion object {
        const val PAYLOAD_UPDATE_ITEM = "updateItem"
    }
}
