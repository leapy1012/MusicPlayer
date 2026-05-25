package gd.app.musicplayer.ui.selection

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.setFragmentResultListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.fueled.draggablerecyclerview.DragItemTouchHelperCallback
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.albumArtSource
import gd.app.musicplayer.core.common.extension.parcelable
import gd.app.musicplayer.core.common.extension.dpToPx
import gd.app.musicplayer.core.common.extension.loadMusicArtwork
import gd.app.musicplayer.core.common.extension.startActivityCompat
import gd.app.musicplayer.core.common.util.ToastUtil
import gd.app.musicplayer.core.designsystem.dialog.MaterialDialogConfigFactory
import gd.app.musicplayer.core.designsystem.dialog.createMessageDialogConfig
import gd.app.musicplayer.core.designsystem.dialog.showMessageDialog
import gd.app.musicplayer.core.designsystem.theme.accentColor
import gd.app.musicplayer.databinding.ActivityPlaylistEditBinding
import gd.app.musicplayer.databinding.ActivityPlaylistEditItemBinding
import gd.app.musicplayer.domain.model.MenuItemModel
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.domain.model.MusicSet
import gd.app.musicplayer.domain.usecase.playback.EnqueueTracksUseCase
import gd.app.musicplayer.domain.usecase.playback.PlayTracksUseCase
import gd.app.musicplayer.domain.usecase.playlist.DeletePlaylistUseCase
import gd.app.musicplayer.domain.usecase.playlist.UpdatePlaylistOrderUseCase
import gd.app.musicplayer.domain.usecase.library.ObserveTracksUseCase
import gd.app.musicplayer.feature.playlist.PlaylistSelectActivity
import gd.app.musicplayer.feature.playlist.PlaylistInputDialog
import gd.app.musicplayer.ui.common.base.BaseActivity
import gd.app.musicplayer.ui.common.base.RecyclerEmptyStateController
import gd.app.musicplayer.ui.common.base.setupEdgeToEdgeToolbar
import gd.app.musicplayer.ui.common.menu.ContextMenuAction
import gd.app.musicplayer.ui.common.menu.EditMorePopupMenu
import gd.app.musicplayer.ui.common.menu.OnItemClickListener
import gd.app.musicplayer.ui.common.model.loadArtwork
import gd.app.musicplayer.ui.common.model.resolvePlaceholderRes
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Collections
import javax.inject.Inject

@AndroidEntryPoint
class PlaylistEditActivity : BaseActivity() {

    @Inject lateinit var observeTracksUseCase: ObserveTracksUseCase
    @Inject lateinit var playTracksUseCase: PlayTracksUseCase
    @Inject lateinit var enqueueTracksUseCase: EnqueueTracksUseCase
    @Inject lateinit var deletePlaylistUseCase: DeletePlaylistUseCase
    @Inject lateinit var updatePlaylistOrderUseCase: UpdatePlaylistOrderUseCase
    @Inject lateinit var materialDialogConfigFactory: MaterialDialogConfigFactory

    private lateinit var binding: ActivityPlaylistEditBinding
    private lateinit var adapter: PlaylistEditAdapter
    private lateinit var emptyStateController: RecyclerEmptyStateController
    private lateinit var selectAllImage: ImageView
    private lateinit var itemTouchHelper: ItemTouchHelper

    private val selectedIds = linkedSetOf<Long>()
    private var currentItems: List<MusicSet.Playlist> = emptyList()
    private val preloadedItems by lazy { readPreloadedItemsFromIntent() }

    private val initialSelectedId by lazy {
        intent.getLongExtra(EXTRA_PRESELECTED_ID, -1L)
    }

    private val preferredIds by lazy { readPreferredIdsFromIntent() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlaylistEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecycler()
        setupBottomActions()
        registerRenameResultListener()
        loadInitialItems()
    }

    private fun setupToolbar() {
        setupEdgeToEdgeToolbar(
            root = binding.root,
            statusBarView = binding.statusBarSpace,
            bottomPaddingView = binding.musicEditLayout,
            toolbar = binding.toolbar
        )

        selectAllImage = binding.toolbar.installSelectAllAction(layoutInflater) {
            toggleSelectAll()
        }
    }

    private fun setupRecycler() {
        adapter = PlaylistEditAdapter(
            onToggleSelection = ::toggleSelection,
            onOrderChanged = ::onOrderChanged,
            startDrag = { holder -> itemTouchHelper.startDrag(holder) },
            accentColor = themeRepo.getCorePalette().accentColor
        )
        binding.layoutRecyclerview.recyclerview.layoutManager =
            LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        binding.layoutRecyclerview.recyclerview.adapter = adapter
        (binding.layoutRecyclerview.recyclerview.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false

        val callback = DragItemTouchHelperCallback.Builder(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            0
        )
            .setDragEnabled(false)
            .onItemDragListener(adapter::onItemMove)
            .onDragFinishedListener(adapter::onDragFinished)
            .build()
        itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper.attachToRecyclerView(binding.layoutRecyclerview.recyclerview)

        emptyStateController = RecyclerEmptyStateController(
            recyclerView = binding.layoutRecyclerview.recyclerview,
            emptyViewStub = binding.layoutRecyclerview.layoutListEmpty
        ).apply {
            setEmptyMessage(getString(R.string.playlist_is_empty))
        }
    }

    private fun setupBottomActions() {
        binding.mainInfoPlay.setOnClickListener { handleAction(ContextMenuAction.ShuffleAll) }
        binding.mainInfoAdd.setOnClickListener { handleAction(ContextMenuAction.AddToPlaylist) }
        binding.mainInfoEnqueue.setOnClickListener { handleAction(ContextMenuAction.AddToQueue) }
        binding.mainInfoDelete.setOnClickListener { deleteSelectedPlaylists() }
        binding.mainInfoMore.setOnClickListener {
            showMorePopup(binding.mainInfoMoreImage)
        }
    }

    private fun loadInitialItems() {
        // Reference behavior is list-snapshot driven for this screen.
        val snapshot = when {
            preloadedItems.isNotEmpty() -> preloadedItems
            preferredIds.isNotEmpty() -> preloadedItems.filter { it.id in preferredIds.toHashSet() }
            else -> preloadedItems
        }.filter { it.id > 1L }

        currentItems = snapshot
        if (selectedIds.isEmpty() && initialSelectedId > 1L) {
            selectedIds.add(initialSelectedId)
        }
        selectedIds.retainAll(snapshot.mapTo(hashSetOf()) { it.id })
        adapter.submit(snapshot, selectedIds)
        renderSelectionUi()
        emptyStateController.setVisible(snapshot.isEmpty())
    }

    private fun toggleSelection(item: MusicSet.Playlist) {
        if (!selectedIds.add(item.id)) {
            selectedIds.remove(item.id)
        }
        adapter.submit(currentItems, selectedIds)
        renderSelectionUi()
    }

    private fun toggleSelectAll() {
        val allIds = currentItems.mapTo(linkedSetOf()) { it.id }
        if (allIds.isEmpty()) return
        if (selectedIds.containsAll(allIds)) {
            selectedIds.clear()
        } else {
            selectedIds.clear()
            selectedIds.addAll(allIds)
        }
        adapter.submit(currentItems, selectedIds)
        renderSelectionUi()
    }

    private fun renderSelectionUi() {
        val count = selectedIds.size
        selectAllImage.renderSelectAllState(
            SelectionUiState(
                selectedCount = count,
                selectableCount = currentItems.size
            )
        )
        binding.toolbar.title = if (count == 1) {
            getString(R.string.playlist_selected, count)
        } else {
            getString(R.string.playlists_selected, count)
        }
    }

    private fun selectedPlaylists(): List<MusicSet.Playlist> {
        return currentItems.filter { it.id in selectedIds }
    }

    private fun handleAction(action: ContextMenuAction) {
        val selected = selectedPlaylists()
        if (selected.isEmpty()) {
            ToastUtil.show(this, R.string.playlist_is_empty)
            return
        }
        lifecycleScope.launch {
            val tracks = resolveTracks(selected)
            if (tracks.isEmpty()) {
                ToastUtil.show(this@PlaylistEditActivity, R.string.select_musics_empty)
                return@launch
            }
            when (action) {
                ContextMenuAction.ShuffleAll -> {
                    ToastUtil.show(
                        this@PlaylistEditActivity,
                        getString(R.string.edit_play_tips, tracks.size)
                    )
                    playTracksUseCase(tracks, 0)
                    clearSelection()
                }

                ContextMenuAction.AddToQueue -> {
                    ToastUtil.show(
                        this@PlaylistEditActivity,
                        getString(R.string.enqueue_msg_count, tracks.size)
                    )
                    enqueueTracksUseCase(tracks)
                    clearSelection()
                }

                ContextMenuAction.AddToPlaylist -> {
                    PlaylistSelectActivity.start(this@PlaylistEditActivity, tracks)
                    clearSelection()
                }

                else -> Unit
            }
        }
    }

    private fun deleteSelectedPlaylists() {
        val selected = selectedPlaylists()
        if (selected.isEmpty()) {
            ToastUtil.show(this, R.string.playlist_is_empty)
            return
        }

        val message = if (selected.size == 1) {
            getString(R.string.delete_playlist_x, selected.first().name)
        } else {
            getString(R.string.delete_x_playlists, selected.size)
        }
        val config = materialDialogConfigFactory
            .createMaterialMessageDialogConfig(this)
            .apply {
                titleText = getString(R.string.delete_playlist)
                messageText = message
                positiveButtonText = getString(R.string.ok)
                negativeButtonText = getString(R.string.cancel)
                positiveButtonClickListener = DialogInterface.OnClickListener { dialog, _ ->
                    dialog.dismiss()
                    val deletedIds = selected.mapTo(hashSetOf()) { it.id }

                    // Keep UI in sync immediately for this snapshot-driven screen.
                    currentItems = currentItems.filterNot { it.id in deletedIds }
                    selectedIds.removeAll(deletedIds)
                    adapter.submit(currentItems, selectedIds)
                    renderSelectionUi()
                    emptyStateController.setVisible(currentItems.isEmpty())

                    lifecycleScope.launch {
                        selected.forEach { playlist ->
                            deletePlaylistUseCase(playlist.id)
                        }
                        ToastUtil.show(this@PlaylistEditActivity, R.string.succeed)
                    }
                }
                negativeButtonClickListener = DialogInterface.OnClickListener { dialog, _ ->
                    dialog.dismiss()
                }
            }
        showMessageDialog(config)
    }

    private suspend fun resolveTracks(playlists: List<MusicSet.Playlist>): List<Music> {
        return playlists
            .flatMap { playlist -> observeTracksUseCase(playlist).first() }
            .distinctBy(Music::id)
    }

    private fun onOrderChanged(reordered: List<MusicSet.Playlist>) {
        currentItems = reordered
        lifecycleScope.launch {
            updatePlaylistOrderUseCase(reordered.map { it.id })
        }
    }

    private fun readPreferredIdsFromIntent(): List<Long> {
        val raw = intent.getLongArrayExtra(EXTRA_PLAYLIST_IDS) ?: return emptyList()
        return raw.toList()
    }

    private fun readPreloadedItemsFromIntent(): List<MusicSet.Playlist> {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(
                EXTRA_PLAYLIST_ITEMS,
                MusicSet.Playlist::class.java
            ) ?: arrayListOf()
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra<MusicSet.Playlist>(EXTRA_PLAYLIST_ITEMS)
                ?: arrayListOf()
        }
    }

    private fun showMorePopup(anchor: View) {
        val selected = selectedPlaylists()
        if (selected.isEmpty()) {
            ToastUtil.show(this, R.string.playlist_is_empty)
            return
        }
        val moreMenuItems = buildMoreMenuItems(selected)
        EditMorePopupMenu(
            context = this,
            items = moreMenuItems,
            theme = themeRepo.getCorePalette(),
            itemClickListener = object : OnItemClickListener<MenuItemModel> {
                override fun onItemClick(item: MenuItemModel, clickedView: View, position: Int) {
                    when (item.getTitleResId()) {
                        R.string.rename -> renameSelectedPlaylist()
                        R.string.share -> shareSelectedPlaylists()
                    }
                }
            }
        ).show(
            anchor = anchor,
            yOff = -calculateMorePopupOffsetPx(moreMenuItems.size)
        )
    }

    private fun buildMoreMenuItems(selected: List<MusicSet.Playlist>): List<MenuItemModel> {
        val items = ArrayList<MenuItemModel>(2)
        if (selected.size == 1 && selected.first().id > 1L) {
            items.add(MenuItemModel.create(R.string.rename))
        }
        items.add(MenuItemModel.create(R.string.share).setIcon(R.drawable.vector_editor_share))
        return items
    }

    private fun renameSelectedPlaylist() {
        val selected = selectedPlaylists()
        val target = selected.singleOrNull()?.takeIf { it.id > 1L } ?: run {
            ToastUtil.show(this, R.string.playlist_is_empty)
            return
        }

        PlaylistInputDialog
            .forSet(
                set = target,
                mode = PlaylistInputDialog.MODE_RENAME_SET
            )
            .show(
                supportFragmentManager,
                TAG_RENAME_PLAYLIST_DIALOG
            )
    }

    private fun shareSelectedPlaylists() {
        val selected = selectedPlaylists()
        if (selected.isEmpty()) {
            ToastUtil.show(this, R.string.playlist_is_empty)
            return
        }

        lifecycleScope.launch {
            val tracks = resolveTracks(selected)
            MusicShareSupport.share(this@PlaylistEditActivity, tracks)
        }
    }

    private fun registerRenameResultListener() {
        supportFragmentManager.setFragmentResultListener(
            PlaylistInputDialog.RESULT_REQUEST_KEY,
            this
        ) { _, bundle ->
            val renamed = bundle.parcelable<MusicSet>(PlaylistInputDialog.RESULT_RENAMED_SET)
                as? MusicSet.Playlist
                ?: return@setFragmentResultListener

            var changed = false
            currentItems = currentItems.map { playlist ->
                if (playlist.id == renamed.id) {
                    changed = true
                    renamed
                } else {
                    playlist
                }
            }
            if (changed) {
                adapter.submit(currentItems, selectedIds)
                renderSelectionUi()
            }
        }
    }

    private fun clearSelection() {
        selectedIds.clear()
        adapter.submit(currentItems, selectedIds)
        renderSelectionUi()
    }

    companion object {
        private const val EXTRA_PLAYLIST_IDS = "extra_playlist_ids"
        private const val EXTRA_PLAYLIST_ITEMS = "extra_playlist_items"
        private const val EXTRA_PRESELECTED_ID = "extra_preselected_playlist_id"
        private const val TAG_RENAME_PLAYLIST_DIALOG = "rename_playlist_dialog"

        fun start(
            context: Context,
            playlists: List<MusicSet.Playlist>,
            preselected: MusicSet.Playlist? = null
        ) {
            val eligible = playlists.filter { it.id > 1L }
            val ids = eligible.map { it.id }.toLongArray()
            context.startActivityCompat(
                Intent(context, PlaylistEditActivity::class.java).apply {
                    putExtra(EXTRA_PLAYLIST_IDS, ids)
                    putParcelableArrayListExtra(EXTRA_PLAYLIST_ITEMS, ArrayList(eligible))
                    putExtra(EXTRA_PRESELECTED_ID, preselected?.id ?: -1L)
                }
            )
        }
    }
}

private fun Context.calculateMorePopupOffsetPx(itemCount: Int): Int {
    val itemHeightPx = dpToPx(48f)
    return (itemHeightPx * itemCount) + 10
}

private class PlaylistEditAdapter(
    private val onToggleSelection: (MusicSet.Playlist) -> Unit,
    private val onOrderChanged: (List<MusicSet.Playlist>) -> Unit,
    private val startDrag: (RecyclerView.ViewHolder) -> Unit,
    private val accentColor: Int
) : RecyclerView.Adapter<PlaylistEditAdapter.ViewHolder>() {

    private val selectedIds = linkedSetOf<Long>()
    private val items = mutableListOf<MusicSet.Playlist>()

    init {
        setHasStableIds(true)
    }

    fun submit(
        items: List<MusicSet.Playlist>,
        selected: Set<Long>
    ) {
        this.items.clear()
        this.items.addAll(items)
        selectedIds.clear()
        selectedIds.addAll(selected)
        notifyDataSetChanged()
    }

    fun onItemMove(fromPosition: Int, toPosition: Int) {
        if (fromPosition !in items.indices || toPosition !in items.indices) return
        if (fromPosition == toPosition) return

        if (fromPosition < toPosition) {
            for (index in fromPosition until toPosition) {
                Collections.swap(items, index, index + 1)
            }
        } else {
            for (index in fromPosition downTo toPosition + 1) {
                Collections.swap(items, index, index - 1)
            }
        }
        notifyItemMoved(fromPosition, toPosition)
    }

    fun onDragFinished() {
        onOrderChanged(items.toList())
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return ViewHolder(
            binding = ActivityPlaylistEditItemBinding.inflate(inflater, parent, false),
            onToggleSelection = onToggleSelection,
            startDrag = startDrag,
            accentColor = accentColor
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item, item.id in selectedIds)
    }

    override fun getItemCount(): Int = items.size

    override fun getItemId(position: Int): Long {
        return items.getOrNull(position)?.id ?: RecyclerView.NO_ID
    }

    class ViewHolder(
        private val binding: ActivityPlaylistEditItemBinding,
        private val onToggleSelection: (MusicSet.Playlist) -> Unit,
        private val startDrag: (RecyclerView.ViewHolder) -> Unit,
        private val accentColor: Int
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(
            item: MusicSet.Playlist,
            selected: Boolean
        ) {
            binding.musicItemTitle.text = item.name
            binding.musicItemExtra.text =
                "${item.musicCount} ${itemView.context.getString(R.string.songs)}"
            binding.musicItemCheckbox.setImageResource(
                if (selected) R.drawable.vector_multi_checked else R.drawable.vector_multi_unchecked
            )
            item.loadArtwork(binding.musicItemAlbum, item.resolvePlaceholderRes(false))
            binding.musicItemCheckbox.isSelected = selected
            binding.musicItemCheckbox.setColorFilter(
                if (selected) {
                    accentColor
                } else {
                    Color.WHITE
                }
            )
            binding.root.setOnClickListener { onToggleSelection(item) }
            binding.musicItemCheckbox.setOnClickListener { onToggleSelection(item) }
            binding.musicItemDrag.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    startDrag(this)
                    true
                } else {
                    false
                }
            }
        }
    }
}
