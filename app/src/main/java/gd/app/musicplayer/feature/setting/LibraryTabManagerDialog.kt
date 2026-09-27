package gd.app.musicplayer.feature.setting

import android.app.Dialog
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ImageSpan
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.coui.appcompat.dialog.COUIAlertDialogBuilder
import com.fueled.draggablerecyclerview.DragItemTouchHelperCallback
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.dpToPx
import gd.app.musicplayer.core.datastore.SettingPreferencesDataStore
import gd.app.musicplayer.databinding.DialogTabManagerBinding
import gd.app.musicplayer.databinding.DialogTabManagerItemBinding
import gd.app.musicplayer.domain.model.LibraryTabConfig
import gd.app.musicplayer.domain.model.LibraryTabConfigStore
import gd.app.musicplayer.ui.selection.ItemMoveListener
import java.util.Collections
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class LibraryTabManagerDialog : DialogFragment() {

    @Inject lateinit var settingPreferencesDataStore: SettingPreferencesDataStore

    private var binding: DialogTabManagerBinding? = null
    private lateinit var adapter: LibraryTabManagerAdapter
    private lateinit var itemTouchHelper: ItemTouchHelper
    private var items: MutableList<LibraryTabConfig> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        items = restoreItems(savedInstanceState).toMutableList()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val contentBinding = DialogTabManagerBinding.inflate(LayoutInflater.from(requireContext()))
        binding = contentBinding

        adapter = LibraryTabManagerAdapter(
            items = items,
            onStartDrag = { holder -> itemTouchHelper.startDrag(holder) }
        )
        contentBinding.tabManagerRecycler.layoutManager = LinearLayoutManager(requireContext())
        contentBinding.tabManagerRecycler.adapter = adapter

        val callback = DragItemTouchHelperCallback.Builder(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            0
        )
            .setDragEnabled(false)
            .onItemDragListener(adapter::onItemMove)
            .build()
        itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper.attachToRecyclerView(contentBinding.tabManagerRecycler)

        if (savedInstanceState == null) {
            lifecycleScope.launch {
                adapter.replaceItems(settingPreferencesDataStore.getLibraryTabConfig())
            }
        }

        val dialog = COUIAlertDialogBuilder(
            requireContext(),
            com.coui.appcompat.R.style.COUIAlertDialog_List
        )
            .setTitle(buildTitle())
            .setView(contentBinding.root)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.ok, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                lifecycleScope.launch {
                    saveSelection()
                    parentFragmentManager.setFragmentResult(RESULT_KEY, bundleOf())
                    dismiss()
                }
            }
        }
        return dialog
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putIntArray(STATE_IDS, items.map(LibraryTabConfig::id).toIntArray())
        outState.putBooleanArray(STATE_VISIBLE, items.map(LibraryTabConfig::visible).toBooleanArray())
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    private fun restoreItems(savedInstanceState: Bundle?): List<LibraryTabConfig> {
        val ids = savedInstanceState?.getIntArray(STATE_IDS)
        val visible = savedInstanceState?.getBooleanArray(STATE_VISIBLE)
        if (ids != null && visible != null && ids.size == visible.size) {
            return ids.mapIndexed { index, id -> LibraryTabConfig(id, visible[index]) }
        }
        return LibraryTabConfigStore.defaultItems
    }

    private fun buildTitle(): CharSequence {
        val title = getString(R.string.tab_manager_title)
        val placeholderStart = title.indexOf("%s")
        if (placeholderStart < 0) return title

        val spannableTitle = SpannableString(title)
        val drawable = ContextCompat.getDrawable(requireContext(), R.drawable.vector_item_drag_black)
            ?: return title.replace("%s", "")
        val icon = DrawableCompat.wrap(drawable.mutate())
        val typedValue = android.util.TypedValue()
        requireContext().theme.resolveAttribute(
            com.coui.appcompat.R.attr.couiColorPrimaryNeutral,
            typedValue,
            true
        )
        DrawableCompat.setTint(icon, typedValue.data)
        val iconSize = requireContext().dpToPx(24f)
        icon.setBounds(0, 0, iconSize, iconSize)
        spannableTitle.setSpan(
            ImageSpan(icon, ImageSpan.ALIGN_BOTTOM),
            placeholderStart,
            placeholderStart + 2,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        return spannableTitle
    }

    private suspend fun saveSelection() {
        settingPreferencesDataStore.setLibraryTabConfig(items)
        val visibleItems = LibraryTabConfigStore.visibleItems(items)
        val lastTab = settingPreferencesDataStore.getLibraryLastTab()
        if (visibleItems.none { it.id == lastTab }) {
            settingPreferencesDataStore.setLibraryLastTab(visibleItems.first().id)
        }
    }

    private class LibraryTabManagerAdapter(
        private val items: MutableList<LibraryTabConfig>,
        private val onStartDrag: (RecyclerView.ViewHolder) -> Unit
    ) : RecyclerView.Adapter<LibraryTabManagerAdapter.ViewHolder>(), ItemMoveListener {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val itemBinding = DialogTabManagerItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return ViewHolder(itemBinding, ::visibleCount, onStartDrag)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size

        override fun onItemMove(fromPosition: Int, toPosition: Int) {
            if (fromPosition !in items.indices || toPosition !in items.indices) return
            Collections.swap(items, fromPosition, toPosition)
            notifyItemMoved(fromPosition, toPosition)
        }

        private fun visibleCount(): Int = items.count(LibraryTabConfig::visible)

        fun replaceItems(newItems: List<LibraryTabConfig>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        class ViewHolder(
            private val binding: DialogTabManagerItemBinding,
            private val visibleCount: () -> Int,
            private val onStartDrag: (RecyclerView.ViewHolder) -> Unit
        ) : RecyclerView.ViewHolder(binding.root), View.OnClickListener {

            private var item: LibraryTabConfig? = null

            init {
                binding.tabManagerItemSelect.setOnClickListener(this)
                binding.tabManagerItemDrag.setOnTouchListener { _, event ->
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                        onStartDrag(this)
                    }
                    false
                }
            }

            fun bind(item: LibraryTabConfig) {
                this.item = item
                binding.tabManagerItemText.setText(LibraryTabConfigStore.labelRes(item.id))
                binding.tabManagerItemSelect.isSelected = item.visible
                itemView.alpha = 1f
            }

            override fun onClick(v: View) {
                val current = item ?: return
                if (!current.visible || visibleCount() > 1) {
                    val position = bindingAdapterPosition
                    if (position == RecyclerView.NO_POSITION) return
                    val updated = current.copy(visible = !current.visible)
                    (bindingAdapter as? LibraryTabManagerAdapter)?.items?.set(position, updated)
                    item = updated
                    binding.tabManagerItemSelect.isSelected = updated.visible
                }
            }
        }
    }

    companion object {
        const val RESULT_KEY = "library_tab_manager_result"
        private const val STATE_IDS = "state_ids"
        private const val STATE_VISIBLE = "state_visible"
    }
}
