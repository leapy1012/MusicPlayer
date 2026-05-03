package gd.app.musicplayer.ui.feature.setting

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import gd.app.musicplayer.databinding.DialogTabManagerBinding
import gd.app.musicplayer.databinding.DialogTabManagerItemBinding
import gd.app.musicplayer.ui.feature.selection.DragSwipeCallback
import gd.app.musicplayer.ui.feature.selection.ItemMoveListener
import gd.app.musicplayer.ui.feature.selection.ItemTouchStateListener
import gd.app.musicplayer.core.ui.dialog.BaseDialogFragment
import gd.app.musicplayer.util.LibraryTabConfig
import gd.app.musicplayer.util.LibraryTabConfigStore
import gd.app.musicplayer.util.PreferenceUtil
import java.util.Collections

class LibraryTabManagerDialog : BaseDialogFragment(), View.OnClickListener {

    private var _binding: DialogTabManagerBinding? = null
    private val binding: DialogTabManagerBinding
        get() = requireNotNull(_binding)

    private lateinit var preferenceUtil: PreferenceUtil
    private lateinit var adapter: LibraryTabManagerAdapter
    private lateinit var itemTouchHelper: ItemTouchHelper
    private var items: MutableList<LibraryTabConfig> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferenceUtil = PreferenceUtil.getInstance(requireContext())
        items = restoreItems(savedInstanceState).toMutableList()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogTabManagerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        applyDialogBackground(binding.root)

        adapter = LibraryTabManagerAdapter(items) { holder ->
            itemTouchHelper.startDrag(holder)
        }
        binding.tabManagerRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.tabManagerRecycler.adapter = adapter

        val callback = DragSwipeCallback(null).apply {
            setLongPressDragEnabled(false)
            setDragDirections(ItemTouchHelper.UP or ItemTouchHelper.DOWN)
        }
        itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper.attachToRecyclerView(binding.tabManagerRecycler)

        binding.dialogButtonCancel.setOnClickListener(this)
        binding.dialogButtonOk.setOnClickListener(this)
    }

    override fun onStart() {
        super.onStart()
        applyDialogWidth(0.9f)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putIntArray(STATE_IDS, items.map(LibraryTabConfig::id).toIntArray())
        outState.putBooleanArray(STATE_VISIBLE, items.map(LibraryTabConfig::visible).toBooleanArray())
    }

    override fun onClick(view: View) {
        when (view.id) {
            binding.dialogButtonCancel.id -> dismiss()
            binding.dialogButtonOk.id -> {
                saveSelection()
                parentFragmentManager.setFragmentResult(RESULT_KEY, bundleOf())
                dismiss()
            }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun restoreItems(savedInstanceState: Bundle?): List<LibraryTabConfig> {
        val ids = savedInstanceState?.getIntArray(STATE_IDS)
        val visible = savedInstanceState?.getBooleanArray(STATE_VISIBLE)
        if (ids != null && visible != null && ids.size == visible.size) {
            return ids.mapIndexed { index, id -> LibraryTabConfig(id, visible[index]) }
        }
        return preferenceUtil.getLibraryTabConfigs()
    }

    private fun saveSelection() {
        preferenceUtil.setLibraryTabConfigs(items)
        val visibleItems = LibraryTabConfigStore.visibleItems(items)
        val lastTab = preferenceUtil.getLibraryLastTab()
        if (visibleItems.none { it.id == lastTab }) {
            preferenceUtil.setLibraryLastTab(visibleItems.first().id)
        }
    }

    private class LibraryTabManagerAdapter(
        private val items: MutableList<LibraryTabConfig>,
        private val onStartDrag: (RecyclerView.ViewHolder) -> Unit
    ) : RecyclerView.Adapter<LibraryTabManagerAdapter.ViewHolder>(), ItemMoveListener {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(
                DialogTabManagerItemBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                ),
                ::visibleCount,
                onStartDrag
            )
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size

        override fun onItemMove(fromPosition: Int, toPosition: Int) {
            if (fromPosition !in items.indices || toPosition !in items.indices) return
            Collections.swap(items, fromPosition, toPosition)
        }

        private fun visibleCount(): Int = items.count(LibraryTabConfig::visible)

        class ViewHolder(
            private val binding: DialogTabManagerItemBinding,
            private val visibleCount: () -> Int,
            private val onStartDrag: (RecyclerView.ViewHolder) -> Unit
        ) : RecyclerView.ViewHolder(binding.root), View.OnClickListener, ItemTouchStateListener {

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
                    current.visible = !current.visible
                    binding.tabManagerItemSelect.isSelected = current.visible
                }
            }

            override fun onItemSelected() {
                itemView.alpha = 0.8f
            }

            override fun onItemCleared() {
                itemView.alpha = 1f
            }
        }
    }

    companion object {
        const val RESULT_KEY = "library_tab_manager_result"
        private const val STATE_IDS = "state_ids"
        private const val STATE_VISIBLE = "state_visible"
    }
}
