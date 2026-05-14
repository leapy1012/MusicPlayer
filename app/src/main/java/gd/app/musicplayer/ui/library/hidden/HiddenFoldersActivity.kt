package gd.app.musicplayer.ui.library.hidden

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.startActivityCompat
import gd.app.musicplayer.databinding.ActivityHiddenFoldersBinding
import gd.app.musicplayer.databinding.ActivityHiddenFoldersItemBinding
import gd.app.musicplayer.databinding.ActivityHiddenFoldersMusicHeaderBinding
import gd.app.musicplayer.databinding.ActivityHiddenFoldersMusicItemBinding
import gd.app.musicplayer.databinding.ActivityHiddenFoldersSetHeaderBinding
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.domain.model.MusicSet
import gd.app.musicplayer.ui.common.base.BaseActivity
import gd.app.musicplayer.ui.common.base.RecyclerEmptyStateController
import gd.app.musicplayer.ui.common.base.setupEdgeToEdgeToolbar
import gd.app.musicplayer.ui.common.viewholder.HiddenFolderHeaderViewHolder
import gd.app.musicplayer.ui.common.viewholder.HiddenFolderViewHolder
import gd.app.musicplayer.ui.common.viewholder.HiddenMusicHeaderViewHolder
import gd.app.musicplayer.ui.common.viewholder.HiddenMusicViewHolder
import kotlinx.coroutines.launch

@AndroidEntryPoint
class HiddenFoldersActivity : BaseActivity() {

    private val viewModel: HiddenFoldersViewModel by viewModels()

    private lateinit var binding: ActivityHiddenFoldersBinding
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
                true
            } else {
                false
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = HiddenItemsAdapter(
            applyTheme = { view ->
                themeEngine.apply(view)
            },
            onRemoveFolder = { folder ->
                viewModel.removeHiddenFolder(folder.folderPath)
            },
            onRemoveMusic = { music ->
                viewModel.unhideSong(music.id)
            }
        )

        binding.root.findViewById<RecyclerView>(R.id.recyclerview).apply {
            layoutManager = LinearLayoutManager(this@HiddenFoldersActivity)
            adapter = this@HiddenFoldersActivity.adapter
            setHasFixedSize(true)

            (itemAnimator as? SimpleItemAnimator)
                ?.supportsChangeAnimations = false
        }
    }

    private fun setupEmptyStateController() {
        emptyStateController = RecyclerEmptyStateController(
            recyclerView = binding.root.findViewById(R.id.recyclerview),
            emptyViewStub = findViewById(R.id.layout_list_empty)
        ).apply {
            setActionButtonVisible(true)
            setExtraTextVisible(false)
            setActionButtonText(getString(R.string.add_files))
            setActionClickListener {
                HiddenFoldersAddActivity.start(this@HiddenFoldersActivity)
            }
            setEmptyImage(R.drawable.folder_empty_image)
            setEmptyMessage(getString(R.string.no_hidden_folders))
        }
    }

    private fun observeHiddenItems() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    private fun render(state: HiddenFoldersUiState) {
        adapter.submitHiddenData(
            folders = state.hiddenFolders,
            songs = state.hiddenSongs
        )

        binding.toolbar.menu.findItem(R.id.menu_add)?.isVisible = !state.isEmpty
        emptyStateController.setVisible(state.isEmpty)
    }

    companion object {
        fun start(context: Context) {
            context.startActivityCompat(
                Intent(context, HiddenFoldersActivity::class.java)
            )
        }
    }
}

private class HiddenItemsAdapter(
    private val applyTheme: (View) -> Unit,
    private val onRemoveFolder: (MusicSet.Folder) -> Unit,
    private val onRemoveMusic: (Music) -> Unit
) : ListAdapter<HiddenRow, RecyclerView.ViewHolder>(HiddenRowDiffCallback) {

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        return getItem(position).stableId
    }

    fun submitHiddenData(
        folders: List<MusicSet.Folder>,
        songs: List<Music>
    ) {
        submitList(
            buildRows(
                folders = folders,
                songs = songs
            )
        )
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is HiddenRow.FolderHeader -> VIEW_TYPE_FOLDER_HEADER
            is HiddenRow.FolderItem -> VIEW_TYPE_FOLDER
            is HiddenRow.MusicHeader -> VIEW_TYPE_MUSIC_HEADER
            is HiddenRow.MusicItem -> VIEW_TYPE_MUSIC
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)

        return when (viewType) {
            VIEW_TYPE_FOLDER_HEADER -> {
                val binding = ActivityHiddenFoldersSetHeaderBinding.inflate(
                    inflater,
                    parent,
                    false
                )

                applyTheme(binding.root)

                HiddenFolderHeaderViewHolder(binding)
            }

            VIEW_TYPE_FOLDER -> {
                val binding = ActivityHiddenFoldersItemBinding.inflate(
                    inflater,
                    parent,
                    false
                )

                applyTheme(binding.root)

                HiddenFolderViewHolder(
                    binding,
                    onRemoveFolder
                )
            }

            VIEW_TYPE_MUSIC_HEADER -> {
                val binding = ActivityHiddenFoldersMusicHeaderBinding.inflate(
                    inflater,
                    parent,
                    false
                )

                applyTheme(binding.root)

                HiddenMusicHeaderViewHolder(binding)
            }

            VIEW_TYPE_MUSIC -> {
                val binding = ActivityHiddenFoldersMusicItemBinding.inflate(
                    inflater,
                    parent,
                    false
                )

                applyTheme(binding.root)

                HiddenMusicViewHolder(
                    binding,
                    onRemoveMusic
                )
            }

            else -> {
                error("Unsupported hidden item view type: $viewType")
            }
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int
    ) {
        when (val row = getItem(position)) {
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

    private fun buildRows(
        folders: List<MusicSet.Folder>,
        songs: List<Music>
    ): List<HiddenRow> {
        val hasFolders = folders.isNotEmpty()
        val hasSongs = songs.isNotEmpty()

        return buildList {
            if (hasFolders && hasSongs) {
                add(HiddenRow.FolderHeader(folders.size))
            }

            folders.forEach { folder ->
                add(HiddenRow.FolderItem(folder))
            }

            if (hasFolders && hasSongs) {
                add(HiddenRow.MusicHeader(songs.size))
            }

            songs.forEach { song ->
                add(HiddenRow.MusicItem(song))
            }
        }
    }

    private companion object {
        private const val VIEW_TYPE_FOLDER_HEADER = 0
        private const val VIEW_TYPE_FOLDER = 1
        private const val VIEW_TYPE_MUSIC_HEADER = 2
        private const val VIEW_TYPE_MUSIC = 3
    }
}

private sealed interface HiddenRow {
    val stableId: Long

    data class FolderHeader(
        val count: Int
    ) : HiddenRow {
        override val stableId: Long = -1L
    }

    data class FolderItem(
        val folder: MusicSet.Folder
    ) : HiddenRow {
        override val stableId: Long = folder.folderPath.hashCode().toLong()
    }

    data class MusicHeader(
        val count: Int
    ) : HiddenRow {
        override val stableId: Long = -2L
    }

    data class MusicItem(
        val music: Music
    ) : HiddenRow {
        override val stableId: Long = music.id
    }
}

private object HiddenRowDiffCallback : DiffUtil.ItemCallback<HiddenRow>() {

    override fun areItemsTheSame(
        oldItem: HiddenRow,
        newItem: HiddenRow
    ): Boolean {
        return oldItem::class == newItem::class &&
                oldItem.stableId == newItem.stableId
    }

    override fun areContentsTheSame(
        oldItem: HiddenRow,
        newItem: HiddenRow
    ): Boolean {
        return oldItem == newItem
    }
}