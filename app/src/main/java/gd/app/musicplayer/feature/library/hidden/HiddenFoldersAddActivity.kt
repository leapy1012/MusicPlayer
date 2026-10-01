package gd.app.musicplayer.feature.library.hidden

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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.coui.appcompat.checkbox.COUICheckBox
import com.coui.appcompat.searchview.COUISearchBar
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.albumArtSource
import gd.app.musicplayer.core.common.extension.applyRoundedOutline
import gd.app.musicplayer.core.common.extension.hideKeyboard
import gd.app.musicplayer.core.common.extension.highlight
import gd.app.musicplayer.core.common.extension.installCouiPressFeedback
import gd.app.musicplayer.core.common.extension.loadMusicArtwork
import gd.app.musicplayer.core.common.extension.startActivityCompat
import gd.app.musicplayer.core.designsystem.drawable.rectRippleDrawable
import gd.app.musicplayer.core.designsystem.theme.ThemeManager
import gd.app.musicplayer.core.designsystem.theme.accentColor
import gd.app.musicplayer.core.designsystem.theme.rippleColor
import gd.app.musicplayer.databinding.ActivityHiddenFoldersAddBinding
import gd.app.musicplayer.databinding.ActivityHiddenFoldersAddItemBinding
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.domain.model.MusicSet
import gd.app.musicplayer.ui.common.base.BaseActivity
import gd.app.musicplayer.ui.common.base.RecyclerEmptyStateController
import gd.app.musicplayer.ui.common.base.inflateThemedMenu
import gd.app.musicplayer.ui.common.base.setupEdgeToEdgeToolbar
import gd.app.musicplayer.ui.common.enableTapToEdit
import gd.app.musicplayer.ui.common.exitEditMode
import gd.app.musicplayer.ui.common.model.loadArtwork
import gd.app.musicplayer.util.SimpleTextWatcher
import java.io.File
import kotlinx.coroutines.launch

@AndroidEntryPoint
class HiddenFoldersAddActivity :
    BaseActivity(),
    Toolbar.OnMenuItemClickListener {

    private val viewModel: HiddenFoldersAddViewModel by viewModels()

    private val currentTheme
        get() = themeEngine.currentTheme()

    private val accentColor: Int
        get() = currentTheme.accentColor

    private val rippleColor: Int
        get() = currentTheme.rippleColor

    private val usesCouiStyling: Boolean
        get() = currentTheme.getThemeType() == ThemeManager.THEME_TYPE_LIGHT

    private val searchWatcher = SimpleTextWatcher {
        applyCurrentList()
    }

    private lateinit var binding: ActivityHiddenFoldersAddBinding
    private lateinit var adapter: HiddenFoldersAddAdapter
    private lateinit var emptyStateController: RecyclerEmptyStateController
    private lateinit var recyclerView: RecyclerView

    private var currentMode = HiddenAddMode.FOLDERS
    private var visibleFolders: List<MusicSet.Folder> = emptyList()
    private var visibleSongs: List<Music> = emptyList()

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

        binding.toolbar.inflateThemedMenu(R.menu.menu_activity_hidden_folders_add, ::applyThemeTo)
        binding.toolbar.setOnMenuItemClickListener(this)
        updateModeMenuTitle()
        binding.appBar.bringToFront()
    }

    private fun setupRecyclerView() {
        recyclerView = binding.root.findViewById(R.id.recyclerview)

        adapter = HiddenFoldersAddAdapter(
            accentColor = if (usesCouiStyling) {
                resolveAttrColor(com.coui.appcompat.R.attr.couiColorPrimary, accentColor)
            } else {
                accentColor
            },
            rippleColor = rippleColor,
            usesCouiStyling = usesCouiStyling,
            applyTheme = { root ->
                themeEngine.apply(root)
            }
        ).apply {
            setSelectionCountListener {
                refreshUi()
            }
        }

        recyclerView.apply {
            layoutManager = LinearLayoutManager(this@HiddenFoldersAddActivity)
            adapter = this@HiddenFoldersAddActivity.adapter
            setHasFixedSize(true)

            itemAnimator = null

            if (usesCouiStyling) {
                setBackgroundColor(
                    resolveAttrColor(com.coui.appcompat.R.attr.couiColorCardBackground, Color.WHITE)
                )
            }
        }

        binding.appBar.bindRecyclerView(recyclerView)
    }

    private fun resolveAttrColor(attr: Int, fallback: Int): Int {
        val typed = obtainStyledAttributes(intArrayOf(attr))
        val color = typed.getColor(0, fallback)
        typed.recycle()
        return color
    }

    private fun setupEmptyStateController() {
        emptyStateController = RecyclerEmptyStateController(
            recyclerView = recyclerView,
            emptyViewStub = findViewById(R.id.layout_list_empty)
        )
    }

    private fun setupSearch() {
        val editText = binding.searchBar.searchEditText ?: return
        editText.addTextChangedListener(searchWatcher)
        editText.setOnEditorActionListener { _, _, _ ->
            editText.hideKeyboard()
            false
        }
        // Instant type slides the bar up over the toolbar; this screen filters in place.
        binding.searchBar.setSearchAnimateType(COUISearchBar.TYPE_NON_INSTANT_SEARCH)
        binding.searchBar.enableTapToEdit()
    }

    private fun setupConfirmButton() {
        if (!usesCouiStyling) {
            binding.buttonConfirm.drawableColor = accentColor
        }

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

        updateSearchUi()

        val visibleCount = adapter.submitData(
            mode = currentMode,
            folders = visibleFolders,
            songs = visibleSongs,
            query = query
        )

        when (currentMode) {
            HiddenAddMode.FOLDERS -> {
                emptyStateController.apply {
                    setEmptyImage(R.drawable.folder_empty_image)
                    setEmptyMessage(getString(R.string.no_hidden_folders))
                }
            }

            HiddenAddMode.SONGS -> {
                emptyStateController.apply {
                    setEmptyImage(R.drawable.music_empty_image)
                    setEmptyMessage(getString(R.string.music_empty))
                }
            }
        }

        emptyStateController.setVisible(visibleCount == 0)

        refreshUi()
    }

    private fun updateSearchUi() {
        binding.searchBar.visibility =
            if (currentMode == HiddenAddMode.SONGS) {
                View.VISIBLE
            } else {
                View.GONE
            }
    }

    private fun refreshUi() {
        binding.buttonConfirm.visibility =
            if (adapter.getSelectionCount() > 0) {
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
            viewModel.hideSelection(
                folderPaths = adapter.getSelectedFolderPaths(),
                songIds = adapter.getSelectedSongIds()
            )

            finish()
        }
    }

    private fun updateModeMenuTitle() {
        binding.toolbar.menu.findItem(R.id.menu_switch)?.setIcon(
            if (currentMode == HiddenAddMode.FOLDERS) {
                R.drawable.vector_menu_switch_music
            } else {
                R.drawable.vector_menu_switch_folder
            }
        )
        // The swapped-in icon is untinted (white in XML); re-run the toolbar tag binding.
        themeEngine.apply(binding.toolbar)
    }

    private fun currentQuery(): String {
        return binding.searchBar.searchEditText
            ?.text
            ?.toString()
            .orEmpty()
            .trim()
    }

    override fun onMenuItemClick(item: MenuItem?): Boolean {
        return when (item?.itemId) {
            R.id.menu_switch -> {
                currentMode = currentMode.toggled()

                /*
                 * Prevent old song query from affecting mode switch visually.
                 * If there is text, clearing it will trigger applyCurrentList() once.
                 * If already empty, manually apply.
                 */
                binding.searchBar.searchEditText?.hideKeyboard()
                binding.searchBar.exitEditMode()

                val searchText = binding.searchBar.searchEditText?.text
                if (searchText?.isNotEmpty() == true) {
                    searchText.clear()
                } else {
                    applyCurrentList()
                }

                true
            }

            else -> false
        }
    }

    companion object {
        fun start(context: Context) {
            context.startActivityCompat(
                Intent(context, HiddenFoldersAddActivity::class.java)
            )
        }
    }
}

private enum class HiddenAddMode {
    FOLDERS,
    SONGS;

    fun toggled(): HiddenAddMode {
        return if (this == FOLDERS) {
            SONGS
        } else {
            FOLDERS
        }
    }
}

private sealed class HiddenSelectionItem(
    open val name: String,
    open val description: String
) {

    abstract val stableId: Long

    abstract fun matches(query: String): Boolean

    data class SongItem(
        val song: Music
    ) : HiddenSelectionItem(
        name = song.title,
        description = song.artist
    ) {
        override val stableId: Long = song.id * 2L

        override fun matches(query: String): Boolean {
            return name.contains(query, ignoreCase = true) ||
                    description.contains(query, ignoreCase = true)
        }
    }

    data class FolderItem(
        val folder: MusicSet.Folder
    ) : HiddenSelectionItem(
        name = File(folder.folderPath).name.ifBlank {
            folder.name
        },
        description = folder.folderPath
    ) {
        override val stableId: Long = folder.folderPath.hashCode().toLong() * 2L + 1L

        override fun matches(query: String): Boolean {
            return name.contains(query, ignoreCase = true) ||
                    description.contains(query, ignoreCase = true)
        }
    }
}

private class HiddenFoldersAddAdapter(
    private val accentColor: Int,
    private val rippleColor: Int,
    private val usesCouiStyling: Boolean,
    private val applyTheme: (View) -> Unit
) : RecyclerView.Adapter<HiddenFoldersAddAdapter.ItemViewHolder>() {

    private var visibleItems: List<HiddenSelectionItem> = emptyList()

    private val selectedFolderPaths = linkedSetOf<String>()
    private val selectedSongIds = linkedSetOf<Long>()

    private var searchQuery: String = ""
    private var currentMode: HiddenAddMode = HiddenAddMode.FOLDERS
    private var selectionCountListener: ((Int) -> Unit)? = null

    init {
        setHasStableIds(true)
    }

    override fun getItemCount(): Int {
        return visibleItems.size
    }

    override fun getItemId(position: Int): Long {
        return visibleItems[position].stableId
    }

    override fun getItemViewType(position: Int): Int {
        return when (visibleItems[position]) {
            is HiddenSelectionItem.FolderItem -> MODE_FOLDERS
            is HiddenSelectionItem.SongItem -> MODE_SONGS
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ItemViewHolder {
        val binding = ActivityHiddenFoldersAddItemBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )

        applyTheme(binding.root)
        binding.musicItemAlbum.applyRoundedOutline(R.dimen.item_image_corner_radius)

        return ItemViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: ItemViewHolder,
        position: Int
    ) {
        holder.bind(visibleItems[position])
    }

    fun setSelectionCountListener(listener: ((Int) -> Unit)?) {
        selectionCountListener = listener
    }

    fun getSelectedFolderPaths(): List<String> {
        return selectedFolderPaths.toList()
    }

    fun getSelectedSongIds(): List<Long> {
        return selectedSongIds.toList()
    }

    fun getSelectionCount(): Int {
        return selectedFolderPaths.size + selectedSongIds.size
    }

    fun submitData(
        mode: HiddenAddMode,
        folders: List<MusicSet.Folder>,
        songs: List<Music>,
        query: String
    ): Int {
        val oldMode = currentMode
        val oldQuery = searchQuery

        currentMode = mode
        searchQuery = query

        selectedFolderPaths.retainAll(
            folders.mapTo(hashSetOf()) { folder ->
                folder.folderPath
            }
        )

        selectedSongIds.retainAll(
            songs.mapTo(hashSetOf()) { song ->
                song.id
            }
        )

        val allItems = when (mode) {
            HiddenAddMode.FOLDERS -> {
                folders.map { folder ->
                    HiddenSelectionItem.FolderItem(folder)
                }
            }

            HiddenAddMode.SONGS -> {
                songs.map { song ->
                    HiddenSelectionItem.SongItem(song)
                }
            }
        }

        val newVisibleItems = if (query.isBlank()) {
            allItems
        } else {
            allItems.filter { item ->
                item.matches(query)
            }
        }

        updateVisibleItems(
            newItems = newVisibleItems,
            oldMode = oldMode,
            newMode = mode,
            oldQuery = oldQuery,
            newQuery = query
        )

        selectionCountListener?.invoke(getSelectionCount())

        return newVisibleItems.size
    }

    private fun updateVisibleItems(
        newItems: List<HiddenSelectionItem>,
        oldMode: HiddenAddMode,
        newMode: HiddenAddMode,
        oldQuery: String,
        newQuery: String
    ) {
        val oldItems = visibleItems
        visibleItems = newItems

        val modeChanged = oldMode != newMode
        val queryChanged = oldQuery != newQuery

        val diff = DiffUtil.calculateDiff(
            object : DiffUtil.Callback() {
                override fun getOldListSize(): Int {
                    return oldItems.size
                }

                override fun getNewListSize(): Int {
                    return newItems.size
                }

                override fun areItemsTheSame(
                    oldItemPosition: Int,
                    newItemPosition: Int
                ): Boolean {
                    val old = oldItems[oldItemPosition]
                    val new = newItems[newItemPosition]

                    return old::class == new::class &&
                            old.stableId == new.stableId
                }

                override fun areContentsTheSame(
                    oldItemPosition: Int,
                    newItemPosition: Int
                ): Boolean {
                    if (modeChanged || queryChanged) {
                        return false
                    }

                    return oldItems[oldItemPosition] == newItems[newItemPosition]
                }
            }
        )

        diff.dispatchUpdatesTo(this)
    }

    inner class ItemViewHolder(
        private val binding: ActivityHiddenFoldersAddItemBinding
    ) : RecyclerView.ViewHolder(binding.root), View.OnClickListener {

        private var boundItem: HiddenSelectionItem? = null

        init {
            if (usesCouiStyling) {
                binding.root.installCouiPressFeedback()
            } else {
                binding.root.background = rectRippleDrawable(
                    fillColor = Color.TRANSPARENT,
                    rippleColor = rippleColor
                )
            }

            binding.root.setOnClickListener(this)
        }

        fun bind(item: HiddenSelectionItem) {
            boundItem = item

            when (item) {
                is HiddenSelectionItem.FolderItem -> {
                    bindFolder(item.folder)
                }

                is HiddenSelectionItem.SongItem -> {
                    bindSong(item.song)
                }
            }

        }

        override fun onClick(v: View) {
            when (val item = boundItem) {
                is HiddenSelectionItem.FolderItem -> {
                    toggleFolderSelection(item.folder.folderPath)
                    setChecked(selectedFolderPaths.contains(item.folder.folderPath))
                }

                is HiddenSelectionItem.SongItem -> {
                    toggleSongSelection(item.song.id)
                    setChecked(selectedSongIds.contains(item.song.id))
                }

                null -> Unit
            }

            selectionCountListener?.invoke(getSelectionCount())
        }

        private fun setChecked(checked: Boolean) {
            binding.musicItemCheckbox.setState(
                if (checked) COUICheckBox.SELECT_ALL else COUICheckBox.SELECT_NONE
            )
        }

        private fun bindFolder(folder: MusicSet.Folder) {
            folder.loadArtwork(
                binding.musicItemAlbum,
                R.drawable.main_folder_simple
            )

            binding.musicItemTitle.text = folder.name
            binding.musicItemArtist.text = folder.folderPath

            binding.musicItemDes.text =
                binding.root.resources.getQuantityString(
                    R.plurals.plurals_track,
                    folder.musicCount,
                    folder.musicCount
                )

            binding.musicItemDes.visibility = View.VISIBLE

            setChecked(selectedFolderPaths.contains(folder.folderPath))
        }

        private fun bindSong(song: Music) {
            binding.musicItemAlbum.loadMusicArtwork(song.albumArtSource())

            binding.musicItemTitle.text =
                song.title.highlight(
                    searchQuery,
                    accentColor
                )

            binding.musicItemArtist.text =
                song.artist.highlight(
                    searchQuery,
                    accentColor
                )

            binding.musicItemDes.visibility = View.GONE

            setChecked(selectedSongIds.contains(song.id))
        }

        private fun toggleFolderSelection(folderPath: String) {
            if (!selectedFolderPaths.add(folderPath)) {
                selectedFolderPaths.remove(folderPath)
            }
        }

        private fun toggleSongSelection(songId: Long) {
            if (!selectedSongIds.add(songId)) {
                selectedSongIds.remove(songId)
            }
        }
    }

    companion object {
        private const val MODE_FOLDERS = 0
        private const val MODE_SONGS = 1
    }
}