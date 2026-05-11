package gd.app.musicplayer.ui.library.hidden

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.activity.viewModels
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.highlight
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.domain.model.MusicSet
import gd.app.musicplayer.core.common.extension.loadMusicArtwork
import gd.app.musicplayer.databinding.ActivityHiddenFoldersAddBinding
import gd.app.musicplayer.databinding.ActivityHiddenFoldersAddItemBinding
import gd.app.musicplayer.ui.common.base.BaseActivity
import gd.app.musicplayer.ui.common.base.RecyclerEmptyStateController
import gd.app.musicplayer.ui.common.base.setupEdgeToEdgeToolbar
import gd.app.musicplayer.ui.common.model.loadArtwork
import gd.app.musicplayer.core.designsystem.drawable.DrawableUtil
import gd.app.musicplayer.core.common.extension.startActivityCompat
import gd.app.musicplayer.core.designsystem.theme.accentColor
import gd.app.musicplayer.core.designsystem.theme.rippleColor
import kotlinx.coroutines.launch
import java.io.File

@AndroidEntryPoint
class HiddenFoldersAddActivity : BaseActivity(), Toolbar.OnMenuItemClickListener {

    private val currentTheme
        get() = themeEngine.currentTheme()

    private val accentColor: Int
        get() = currentTheme.accentColor

    private val rippleColor: Int
        get() = currentTheme.rippleColor
    private val viewModel: HiddenFoldersAddViewModel by viewModels()

    private lateinit var binding: ActivityHiddenFoldersAddBinding
    private lateinit var adapter: HiddenFoldersAddAdapter

    private var currentMode = HiddenAddMode.FOLDERS
    private var visibleFolders: List<MusicSet.Folder> = emptyList()
    private var visibleSongs: List<Music> = emptyList()
    private lateinit var emptyStateController: RecyclerEmptyStateController
    private lateinit var recyclerView: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityHiddenFoldersAddBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        setupEmptyStateController()
        setupSearch()
        setupConfirmButton()
        observeVisibleItems()
        refreshUi()
    }


    private fun setupToolbar() {
        setupEdgeToEdgeToolbar(
            root = binding.root,
            statusBarView = binding.statusBarSpace,
            bottomPaddingView = binding.root,
            toolbar = binding.toolbar,
            titleRes = R.string.add_files
        )
        binding.toolbar.inflateMenu(R.menu.menu_activity_hidden_folders_add)
        binding.toolbar.setOnMenuItemClickListener(this@HiddenFoldersAddActivity)
        updateModeMenuTitle()
    }

    private fun setupEmptyStateController() {
        emptyStateController = RecyclerEmptyStateController(
            recyclerView = recyclerView,
            emptyViewStub = findViewById(R.id.layout_list_empty)
        )
    }

    private fun setupRecyclerView() {
        recyclerView = binding.root.findViewById<RecyclerView>(R.id.recyclerview)

        adapter = HiddenFoldersAddAdapter(accentColor, rippleColor, applyTheme = { root ->
            themeEngine.apply(root)
        }).apply {
            setSelectionCountListener { refreshUi() }
        }
        recyclerView.apply {
            layoutManager = LinearLayoutManager(this@HiddenFoldersAddActivity)
            adapter = this@HiddenFoldersAddActivity.adapter
        }
    }

    private fun setupSearch() {
        binding.searchEditText.doAfterTextChanged {
            adapter.filterSongs(currentQuery())
        }
        binding.searchEditClear.setOnClickListener {
            binding.searchEditText.text?.clear()
        }
    }

    private fun setupConfirmButton() {
        binding.buttonConfirm.background = DrawableUtil.roundedRipple(
            accentColor,
            getColor(R.color.ripple_material_dark),
            1000.0f
        )
        binding.buttonConfirm.setOnClickListener {
            applyHideAndFinish()
        }
    }

    private fun observeVisibleItems() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    visibleFolders = state.visibleFolders
                    visibleSongs = state.visibleSongs
                    applyCurrentList()
                }
            }
        }
    }

    private fun applyCurrentList() {
        val query = currentQuery()
        updateSearchUi(query)
        when (currentMode) {
            HiddenAddMode.FOLDERS -> {
                adapter.resetMode(HiddenFoldersAddAdapter.MODE_FOLDERS, query)
                adapter.submitFolders(visibleFolders)
                emptyStateController.apply {
                    setEmptyImage(R.drawable.folder_empty_image)
                    setEmptyMessage(getString(R.string.no_hidden_folders))
                }
                emptyStateController.setVisible(visibleFolders.isEmpty())
            }

            HiddenAddMode.SONGS -> {
                adapter.resetMode(HiddenFoldersAddAdapter.MODE_SONGS, query)
                adapter.submitSongs(visibleSongs)
                adapter.filterSongs(query)
                emptyStateController.apply {
                    setEmptyImage(R.drawable.music_empty_image)
                    setEmptyMessage(getString(R.string.music_empty))
                }
                emptyStateController.setVisible(visibleSongs.isEmpty())
            }
        }
        refreshUi()
    }

    private fun updateSearchUi(query: String) {
        val inSongMode = currentMode == HiddenAddMode.SONGS
        binding.searchContainer.visibility = if (inSongMode) View.VISIBLE else View.GONE
        binding.searchEditClear.visibility =
            if (inSongMode && query.isNotEmpty()) View.VISIBLE else View.GONE
    }

    private fun refreshUi() {
        binding.buttonConfirm.visibility = if (adapter.getSelectionCount() > 0) {
            View.VISIBLE
        } else {
            View.GONE
        }
        updateModeMenuTitle()
    }

    private fun applyHideAndFinish() {
        if (adapter.getSelectionCount() == 0) {
            finish()
            return
        }

        lifecycleScope.launch {
            val selectedFolderPaths = adapter.getSelectedFolderPaths()
            val selectedSongIds = adapter.getSelectedSongIds()
            viewModel.hideSelection(selectedFolderPaths, selectedSongIds)
            finish()
        }
    }

    private fun updateModeMenuTitle() {
        binding.toolbar.menu.findItem(R.id.menu_switch)?.setIcon(
            if (currentMode == HiddenAddMode.FOLDERS) R.drawable.vector_menu_switch_music else R.drawable.vector_menu_switch_folder
        )
    }

    private fun currentQuery(): String =
        binding.searchEditText.text?.toString().orEmpty().trim()

    override fun onMenuItemClick(item: MenuItem?): Boolean {
        return when (item?.itemId) {
            R.id.menu_switch -> {
                currentMode = currentMode.toggled()
                applyCurrentList()
                true
            }

            else -> false
        }
    }

    companion object {
        fun start(context: Context) {
            context.startActivityCompat(Intent(context, HiddenFoldersAddActivity::class.java))
        }
    }
}

private enum class HiddenAddMode {
    FOLDERS,
    SONGS;

    fun toggled(): HiddenAddMode =
        if (this == FOLDERS) SONGS else FOLDERS
}

private sealed class HiddenSelectionItem(
    open val name: String,
    open val description: String
) {
    abstract fun matches(query: String): Boolean

    data class SongItem(
        val song: Music
    ) : HiddenSelectionItem(
        name = song.title,
        description = song.artist
    ) {
        override fun matches(query: String): Boolean =
            name.contains(query, ignoreCase = true) ||
                    description.contains(query, ignoreCase = true)
    }

    data class FolderItem(
        val folder: MusicSet.Folder
    ) : HiddenSelectionItem(
        name = File(folder.folderPath).name.ifBlank { folder.name },
        description = folder.folderPath
    ) {
        override fun matches(query: String): Boolean =
            name.contains(query, ignoreCase = true)
    }
}

private class HiddenFoldersAddAdapter(
    private val accentColor: Int,
    private val rippleColor: Int,
    private val applyTheme: (View) -> Unit

) : RecyclerView.Adapter<HiddenFoldersAddAdapter.ItemViewHolder>() {
    private val allItems = mutableListOf<HiddenSelectionItem>()
    private var visibleItems: List<HiddenSelectionItem> = emptyList()
    private val selectedFolderPaths = linkedSetOf<String>()
    private val selectedSongIds = linkedSetOf<Long>()

    private var searchQuery: String = ""
    private var mode: Int = MODE_FOLDERS
    private var selectionCountListener: ((Int) -> Unit)? = null

    override fun getItemCount(): Int = visibleItems.size

    override fun getItemViewType(position: Int): Int = mode

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val binding = ActivityHiddenFoldersAddItemBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        applyTheme(binding.root)
        return ItemViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        holder.bind(visibleItems[position])
    }

    fun setSelectionCountListener(listener: ((Int) -> Unit)?) {
        selectionCountListener = listener
    }

    fun getSelectedFolderPaths(): List<String> = selectedFolderPaths.toList()

    fun getSelectedSongIds(): List<Long> = selectedSongIds.toList()

    fun getSelectionCount(): Int = selectedFolderPaths.size + selectedSongIds.size

    fun filterSongs(query: String) {
        if (mode != MODE_SONGS) return
        searchQuery = query
        updateVisibleItems(applyFilter(query))
    }

    fun submitSongs(songs: List<Music>) {
        if (mode != MODE_SONGS) return
        selectedSongIds.retainAll(songs.mapTo(hashSetOf()) { it.id })
        allItems.clear()
        songs.forEach { allItems += HiddenSelectionItem.SongItem(it) }
        updateVisibleItems(applyFilter(searchQuery))
        selectionCountListener?.invoke(getSelectionCount())
    }

    fun submitFolders(folders: List<MusicSet.Folder>) {
        if (mode != MODE_FOLDERS) return
        selectedFolderPaths.retainAll(folders.mapTo(hashSetOf()) { it.folderPath })
        allItems.clear()
        folders.forEach { allItems += HiddenSelectionItem.FolderItem(it) }
        updateVisibleItems(allItems.toList())
        selectionCountListener?.invoke(getSelectionCount())
    }

    fun resetMode(newMode: Int, query: String) {
        mode = newMode
        searchQuery = query
        allItems.clear()
        updateVisibleItems(emptyList())
    }

    private fun applyFilter(query: String): List<HiddenSelectionItem> {
        if (query.isBlank()) {
            return allItems.toList()
        }
        val filtered = mutableListOf<HiddenSelectionItem>()
        allItems.forEach { item ->
            if (item.matches(query)) {
                filtered += item
            }
        }
        return filtered
    }

    private fun updateVisibleItems(newItems: List<HiddenSelectionItem>) {
        val oldItems = visibleItems
        visibleItems = newItems
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = oldItems.size
            override fun getNewListSize(): Int = newItems.size

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val old = oldItems[oldItemPosition]
                val new = newItems[newItemPosition]
                return when {
                    old is HiddenSelectionItem.FolderItem && new is HiddenSelectionItem.FolderItem ->
                        old.folder.folderPath == new.folder.folderPath

                    old is HiddenSelectionItem.SongItem && new is HiddenSelectionItem.SongItem ->
                        old.song.id == new.song.id

                    else -> false
                }
            }

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return oldItems[oldItemPosition] == newItems[newItemPosition]
            }
        })
        diff.dispatchUpdatesTo(this)
    }

    inner class ItemViewHolder(
        private val binding: ActivityHiddenFoldersAddItemBinding
    ) : RecyclerView.ViewHolder(binding.root), View.OnClickListener {

        private lateinit var item: HiddenSelectionItem

        init {
            binding.root.background = DrawableUtil.rectRipple(
                fillColor = Color.TRANSPARENT,
                rippleColor = rippleColor
            )
            binding.root.setOnClickListener(this)
        }

        fun bind(item: HiddenSelectionItem) {
            this.item = item
            when (item) {
                is HiddenSelectionItem.FolderItem -> {
                    item.folder.loadArtwork(
                        binding.musicItemAlbum,
                        R.drawable.main_folder_simple
                    )
                    binding.musicItemTitle.text = item.folder.name
                    binding.musicItemArtist.text = item.folder.folderPath
                    binding.musicItemDes.text = binding.root.resources.getQuantityString(
                        R.plurals.plurals_track,
                        item.folder.musicCount,
                        item.folder.musicCount
                    )
                    binding.musicItemDes.visibility = View.VISIBLE
                    binding.musicItemSelect.isSelected =
                        selectedFolderPaths.contains(item.folder.folderPath)
                }

                is HiddenSelectionItem.SongItem -> {
                    item.song.loadMusicArtwork(binding.musicItemAlbum)
                    binding.musicItemTitle.text =
                        item.song.title.highlight(searchQuery, accentColor)
                    binding.musicItemArtist.text =
                        item.song.artist.highlight(searchQuery, accentColor)
                    binding.musicItemDes.visibility = View.GONE
                    binding.musicItemSelect.isSelected = selectedSongIds.contains(item.song.id)
                }
            }
            updateSelectionTint()
        }

        override fun onClick(v: View) {
            when (val current = item) {
                is HiddenSelectionItem.FolderItem -> {
                    if (!selectedFolderPaths.add(current.folder.folderPath)) {
                        selectedFolderPaths.remove(current.folder.folderPath)
                    }
                    binding.musicItemSelect.isSelected =
                        selectedFolderPaths.contains(current.folder.folderPath)
                }

                is HiddenSelectionItem.SongItem -> {
                    if (!selectedSongIds.add(current.song.id)) {
                        selectedSongIds.remove(current.song.id)
                    }
                    binding.musicItemSelect.isSelected = selectedSongIds.contains(current.song.id)
                }
            }
            updateSelectionTint()
            selectionCountListener?.invoke(getSelectionCount())
        }

        private fun updateSelectionTint() = with(binding) {
            musicItemSelect.setColorFilter(
                if (musicItemSelect.isSelected) {
                    accentColor
                } else {
                    ContextCompat.getColor(root.context, R.color.white)
                }
            )
        }
    }

    companion object {
        const val MODE_FOLDERS = 0
        const val MODE_SONGS = 1
    }
}
