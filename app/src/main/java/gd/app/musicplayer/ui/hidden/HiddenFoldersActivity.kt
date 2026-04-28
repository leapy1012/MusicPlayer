package gd.app.musicplayer.ui.hidden

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.data.model.MusicSet
import gd.app.musicplayer.databinding.ActivityHiddenFoldersBinding
import gd.app.musicplayer.databinding.ActivityHiddenFoldersItemBinding
import gd.app.musicplayer.databinding.ActivityHiddenFoldersMusicHeaderBinding
import gd.app.musicplayer.databinding.ActivityHiddenFoldersMusicItemBinding
import gd.app.musicplayer.databinding.ActivityHiddenFoldersSetHeaderBinding
import gd.app.musicplayer.ui.common.base.BaseActivity
import gd.app.musicplayer.ui.common.base.RecyclerEmptyStateController
import gd.app.musicplayer.ui.common.base.setupEdgeToEdgeToolbar
import gd.app.musicplayer.ui.theme.applyCurrentTheme
import gd.app.musicplayer.ui.common.viewholder.HiddenFolderHeaderViewHolder
import gd.app.musicplayer.ui.common.viewholder.HiddenFolderViewHolder
import gd.app.musicplayer.ui.common.viewholder.HiddenMusicHeaderViewHolder
import gd.app.musicplayer.ui.common.viewholder.HiddenMusicViewHolder
import gd.app.musicplayer.core.extension.startActivityCompat
import kotlinx.coroutines.launch

@AndroidEntryPoint
class HiddenFoldersActivity : BaseActivity() {

    private val viewModel: HiddenFoldersViewModel by viewModels()

    private lateinit var binding: ActivityHiddenFoldersBinding
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: HiddenItemsAdapter
    private lateinit var emptyStateController: RecyclerEmptyStateController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityHiddenFoldersBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        setupEmptyStateController()
        observeHiddenItems()
    }

    

    private fun setupToolbar() {
        setupEdgeToEdgeToolbar(
            root = binding.root,
            statusBarView = binding.statusBarSpace,
            bottomPaddingView = binding.root,
            toolbar = binding.toolbar,
            titleRes = R.string.hidden_folders
        )
        binding.toolbar.inflateMenu(R.menu.menu_activity_hidden_folders)
        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            if (menuItem.itemId == R.id.menu_add) {
                HiddenFoldersAddActivity.start(this)
            }
            true
        }
    }

    private fun setupRecyclerView() {
        recyclerView = findViewById(R.id.recyclerview)
        recyclerView.layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        adapter = HiddenItemsAdapter(
            onRemoveFolder = { folder ->
                viewModel.removeHiddenFolder(folder.folderPath)
            },
            onRemoveMusic = { music ->
                viewModel.unhideSong(music.id)
            }
        )
        recyclerView.adapter = adapter
    }

    private fun setupEmptyStateController() {
        emptyStateController = RecyclerEmptyStateController(
            recyclerView = recyclerView,
            emptyViewStub = findViewById(R.id.layout_list_empty)
        ).apply {
            setActionButtonVisible(true)
            setExtraTextVisible(false)
            setActionButtonText(getString(R.string.add_files))
            setActionClickListener { HiddenFoldersAddActivity.start(this@HiddenFoldersActivity) }
            setEmptyImage(R.drawable.folder_empty_image)
            setEmptyMessage(getString(R.string.no_hidden_folders))
        }
    }

    private fun observeHiddenItems() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    val data = HiddenItemsData(
                        hiddenFolders = state.hiddenFolders,
                        hiddenSongs = state.hiddenSongs
                    )
                    adapter.submitData(data)
                    val isEmpty = state.isEmpty
                    binding.toolbar.menu.findItem(R.id.menu_add)?.isVisible = !isEmpty
                    emptyStateController.setVisible(isEmpty)
                }
            }
        }
    }

    companion object {
        fun start(context: Context) {
            context.startActivityCompat(Intent(context, HiddenFoldersActivity::class.java))
        }
    }
}

data class HiddenItemsData(
    val hiddenFolders: List<MusicSet.Folder>,
    val hiddenSongs: List<Music>
)

private class HiddenItemsAdapter(
    private val onRemoveFolder: (MusicSet.Folder) -> Unit,
    private val onRemoveMusic: (Music) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var rows: List<HiddenRow> = emptyList()

    fun submitData(data: HiddenItemsData) {
        val newRows = buildRows(data)
        val oldRows = rows
        rows = newRows
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = oldRows.size
            override fun getNewListSize(): Int = newRows.size

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val old = oldRows[oldItemPosition]
                val new = newRows[newItemPosition]
                return when {
                    old is HiddenRow.FolderHeader && new is HiddenRow.FolderHeader -> true
                    old is HiddenRow.MusicHeader && new is HiddenRow.MusicHeader -> true
                    old is HiddenRow.FolderItem && new is HiddenRow.FolderItem ->
                        old.folder.folderPath == new.folder.folderPath
                    old is HiddenRow.MusicItem && new is HiddenRow.MusicItem ->
                        old.music.id == new.music.id
                    else -> false
                }
            }

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return oldRows[oldItemPosition] == newRows[newItemPosition]
            }
        })
        diff.dispatchUpdatesTo(this)
    }

    override fun getItemCount(): Int = rows.size

    override fun getItemViewType(position: Int): Int {
        return when (rows[position]) {
            is HiddenRow.FolderHeader -> VIEW_TYPE_FOLDER_HEADER
            is HiddenRow.FolderItem -> VIEW_TYPE_FOLDER
            is HiddenRow.MusicHeader -> VIEW_TYPE_MUSIC_HEADER
            is HiddenRow.MusicItem -> VIEW_TYPE_MUSIC
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_FOLDER_HEADER -> {
                val binding = ActivityHiddenFoldersSetHeaderBinding.inflate(inflater, parent, false)
                applyCurrentTheme(binding.root)
                HiddenFolderHeaderViewHolder(binding)
            }

            VIEW_TYPE_FOLDER -> {
                val binding = ActivityHiddenFoldersItemBinding.inflate(inflater, parent, false)
                applyCurrentTheme(binding.root)
                HiddenFolderViewHolder(binding, onRemoveFolder)
            }

            VIEW_TYPE_MUSIC_HEADER -> {
                val binding = ActivityHiddenFoldersMusicHeaderBinding.inflate(inflater, parent, false)
                applyCurrentTheme(binding.root)
                HiddenMusicHeaderViewHolder(binding)
            }

            VIEW_TYPE_MUSIC -> {
                val binding = ActivityHiddenFoldersMusicItemBinding.inflate(inflater, parent, false)
                applyCurrentTheme(binding.root)
                HiddenMusicViewHolder(binding, onRemoveMusic)
            }
            else -> error("Unsupported hidden item view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is HiddenRow.FolderHeader -> {
                (holder as HiddenFolderHeaderViewHolder).bind(row.count)
            }

            is HiddenRow.FolderItem -> {
                (holder as HiddenFolderViewHolder).bind(row.folder)
            }

            is HiddenRow.MusicHeader -> {
                (holder as HiddenMusicHeaderViewHolder).bind(row.count)
            }

            is HiddenRow.MusicItem -> {
                (holder as HiddenMusicViewHolder).bind(row.music)
            }
        }
    }

    private fun buildRows(data: HiddenItemsData): List<HiddenRow> {
        val hasFolders = data.hiddenFolders.isNotEmpty()
        val hasMusics = data.hiddenSongs.isNotEmpty()
        return buildList {
            if (hasFolders && hasMusics) {
                add(HiddenRow.FolderHeader(data.hiddenFolders.size))
            }
            data.hiddenFolders.forEach { add(HiddenRow.FolderItem(it)) }
            if (hasFolders && hasMusics) {
                add(HiddenRow.MusicHeader(data.hiddenSongs.size))
            }
            data.hiddenSongs.forEach { add(HiddenRow.MusicItem(it)) }
        }
    }

    private sealed interface HiddenRow {
        data class FolderHeader(val count: Int) : HiddenRow
        data class FolderItem(val folder: MusicSet.Folder) : HiddenRow
        data class MusicHeader(val count: Int) : HiddenRow
        data class MusicItem(val music: Music) : HiddenRow
    }

    private companion object {
        const val VIEW_TYPE_FOLDER_HEADER = 0
        const val VIEW_TYPE_FOLDER = 1
        const val VIEW_TYPE_MUSIC_HEADER = 2
        const val VIEW_TYPE_MUSIC = 3
    }
}
