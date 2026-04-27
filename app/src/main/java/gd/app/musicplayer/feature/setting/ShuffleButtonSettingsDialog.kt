package gd.app.musicplayer.feature.setting

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import gd.app.musicplayer.R
import gd.app.musicplayer.databinding.DialogShuffleSettingBinding
import gd.app.musicplayer.databinding.DialogShuffleSettingItemBinding
import gd.app.musicplayer.ui.common.base.BaseThemedDialogFragment
import gd.app.musicplayer.util.PreferenceUtil

class ShuffleButtonSettingsDialog : BaseThemedDialogFragment(), View.OnClickListener {

    private var _binding: DialogShuffleSettingBinding? = null
    private val binding: DialogShuffleSettingBinding
        get() = requireNotNull(_binding)

    private lateinit var preferenceUtil: PreferenceUtil
    private lateinit var adapter: ShuffleSettingsAdapter
    private var items: MutableList<ShuffleSettingItem> = mutableListOf()

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
        _binding = DialogShuffleSettingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        applyDialogBackground(binding.root)
        adapter = ShuffleSettingsAdapter(items)
        binding.shuffleSettingRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.shuffleSettingRecycler.adapter = adapter
        binding.dialogButtonOk.setOnClickListener(this)
    }

    override fun onStart() {
        super.onStart()
        applyDialogWidth(0.9f)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putIntArray(STATE_IDS, items.map(ShuffleSettingItem::id).toIntArray())
        outState.putBooleanArray(STATE_SELECTED, items.map(ShuffleSettingItem::enabled).toBooleanArray())
    }

    override fun onClick(view: View) {
        if (view.id != binding.dialogButtonOk.id) return
        saveSelection()
        parentFragmentManager.setFragmentResult(RESULT_KEY, bundleOf())
        dismiss()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun restoreItems(savedInstanceState: Bundle?): List<ShuffleSettingItem> {
        val restoredIds = savedInstanceState?.getIntArray(STATE_IDS)
        val restoredSelection = savedInstanceState?.getBooleanArray(STATE_SELECTED)
        if (restoredIds != null && restoredSelection != null && restoredIds.size == restoredSelection.size) {
            return restoredIds.mapIndexed { index, id ->
                ShuffleSettingItem(id = id, enabled = restoredSelection[index])
            }
        }

        return DEFAULT_TARGETS.map { id ->
            ShuffleSettingItem(id = id, enabled = preferenceUtil.isShowShuffleButtonEnabled(id))
        }
    }

    private fun saveSelection() {
        var anyEnabled = false
        items.forEach { item ->
            preferenceUtil.setShowShuffleButtonEnabled(item.id, item.enabled)
            anyEnabled = anyEnabled || item.enabled
        }
        preferenceUtil.setShowShuffleButtonEnabled(anyEnabled)
    }

    data class ShuffleSettingItem(
        val id: Int,
        var enabled: Boolean
    )

    private class ShuffleSettingsAdapter(
        private val items: List<ShuffleSettingItem>
    ) : RecyclerView.Adapter<ShuffleSettingsAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(
                DialogShuffleSettingItemBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size

        class ViewHolder(
            private val binding: DialogShuffleSettingItemBinding
        ) : RecyclerView.ViewHolder(binding.root), View.OnClickListener {

            private var item: ShuffleSettingItem? = null

            init {
                binding.root.setOnClickListener(this)
            }

            fun bind(item: ShuffleSettingItem) {
                this.item = item
                binding.shuffleSettingItemText.setText(labelRes(item.id))
                binding.shuffleSettingItemImage.isSelected = item.enabled
            }

            override fun onClick(v: View) {
                val current = item ?: return
                current.enabled = !current.enabled
                binding.shuffleSettingItemImage.isSelected = current.enabled
            }

            companion object {
                private fun labelRes(id: Int): Int = when (id) {
                    0 -> R.string.home
                    1 -> R.string.playlist
                    -1 -> R.string.tracks
                    -5 -> R.string.albums
                    -4 -> R.string.artists
                    -8 -> R.string.genres
                    -6 -> R.string.folder
                    else -> R.string.tracks
                }
            }
        }
    }

    companion object {
        const val RESULT_KEY = "shuffle_button_settings_result"
        private const val STATE_IDS = "state_ids"
        private const val STATE_SELECTED = "state_selected"

        private val DEFAULT_TARGETS = listOf(0, 1, -1, -5, -4, -8, -6)
    }
}
