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
import gd.app.musicplayer.core.extension.getMaxScreenSize
import gd.app.musicplayer.core.extension.getMinScreenSize
import gd.app.musicplayer.core.ui.drawable.DrawableUtil

abstract class BaseBottomGridMenuDialog : BaseBottomRecyclerMenuDialog() {

    protected var titleView: TextView? = null
        private set

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
            val index = items.indexOf(item)
            if (index >= 0) {
                notifyItemChanged(index, PAYLOAD_UPDATE_ITEM)
            }
        }

        fun updateItems(newItems: List<MenuItem>) {
            items.clear()
            items.addAll(newItems)
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
            applyCurrentTheme(itemView)
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
        val titleIconView = container.findViewById<ImageView>(R.id.bottom_menu_title_icon)

        titleTextView.maxWidth = calculateTitleMaxWidth(
            requireContext().resources.configuration
        )

        titleView = titleTextView
        onBindTitleArea(container, titleTextView, titleIconView)
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

    private fun applyCurrentTheme(view: View) {
        (activity as? BaseActivity)?.applyThemeTo(view)
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
                    themedView.background = DrawableUtil.rectRipple(Color.TRANSPARENT, pressedColor)
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
