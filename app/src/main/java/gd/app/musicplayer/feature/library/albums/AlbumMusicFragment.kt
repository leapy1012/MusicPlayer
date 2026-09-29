package gd.app.musicplayer.feature.library.albums

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.commit
import androidx.fragment.app.setFragmentResultListener
import androidx.recyclerview.widget.COUIRecyclerView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.appbar.AppBarLayout
import dagger.hilt.android.AndroidEntryPoint
import gd.app.lib.view.MaskImageView
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.navigateBack
import gd.app.musicplayer.core.common.extension.parcelable
import gd.app.musicplayer.core.common.extension.resolveStatusBarHeightPx
import gd.app.musicplayer.core.common.extension.screenHeight
import gd.app.musicplayer.core.common.extension.screenWidth
import gd.app.musicplayer.core.common.extension.supportsCompactAlbumHeader
import gd.app.musicplayer.core.common.util.ToastUtil
import gd.app.musicplayer.domain.model.ArtworkRequest
import gd.app.musicplayer.domain.model.MusicSet
import gd.app.musicplayer.domain.repository.ThemeRepo
import gd.app.musicplayer.databinding.FragmentAlbumMusicBinding
import gd.app.musicplayer.feature.playlist.PlaylistInputDialog
import gd.app.musicplayer.feature.search.SearchActivity
import gd.app.musicplayer.ui.selection.MusicSelectActivity
import gd.app.musicplayer.ui.selection.MusicEditActivity
import gd.app.musicplayer.ui.shortcut.MusicSetShortcutHelper
import gd.app.musicplayer.ui.common.base.ViewBindingFragment
import gd.app.musicplayer.ui.common.base.applyCouiLeftTitle
import gd.app.musicplayer.ui.common.menu.ContextMenu
import gd.app.musicplayer.ui.common.menu.ContextMenuAction
import gd.app.musicplayer.feature.library.ARG_MUSIC_SET
import gd.app.musicplayer.feature.library.artwork.ManageArtworkDialogFragment
import gd.app.musicplayer.feature.library.tracks.TrackListFragment
import gd.app.musicplayer.feature.library.tracks.TrackListSortState
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.min
import androidx.core.view.updatePadding

@AndroidEntryPoint
class AlbumMusicFragment :
    ViewBindingFragment<FragmentAlbumMusicBinding>(),
    Toolbar.OnMenuItemClickListener {

    @Inject lateinit var themeRepo: ThemeRepo

    private lateinit var musicSet: MusicSet

    private val isCompactHeader: Boolean
        get() = musicSet.supportsCompactAlbumHeader

    private val headerPlaceholderResId: Int
        get() = musicSet.headerPlaceholderResId

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        musicSet = readMusicSetFromArgs()
            ?: throw IllegalStateException("AlbumMusicFragment requires MusicSet argument")
    }

    override fun onCreateBinding(
        inflater: LayoutInflater
    ): FragmentAlbumMusicBinding {
        return FragmentAlbumMusicBinding.inflate(inflater)
    }

    override fun onBindingCreated(
        binding: FragmentAlbumMusicBinding,
        savedInstanceState: Bundle?
    ) {
        super.onBindingCreated(binding, savedInstanceState)

        applyEdgeToEdgeInsets(binding)
        setupToolbar(binding)
        setupHeader(binding)
        registerRenameResultListener()
        registerArtworkResultListener()
        setupChildFragment()
        binding.mainChildFragmentContainer.post { bindAppBarToList() }
    }

    private fun applyEdgeToEdgeInsets(binding: FragmentAlbumMusicBinding) {
        // AppBarLayout defaults to consuming system-window insets and padding its children,
        // which parks the album image below the status bar. Keep the header full-bleed and
        // only inset the pinned toolbar content.
        binding.appbarLayout.fitsSystemWindows = false
        binding.collapsingToolbar.fitsSystemWindows = false
        binding.musicsetAlbum.fitsSystemWindows = false
        binding.toolbar.fitsSystemWindows = false

        val statusBarHeight = requireContext().resolveStatusBarHeightPx()
        if (statusBarHeight > 0) {
            binding.toolbar.updatePadding(top = statusBarHeight)
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            if (systemBars.top > 0 && binding.toolbar.paddingTop != systemBars.top) {
                binding.toolbar.updatePadding(top = systemBars.top)
            }
            binding.root.updatePadding(bottom = systemBars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
    }

    private fun setupToolbar(binding: FragmentAlbumMusicBinding) {
        binding.toolbar.apply {
            applyCouiLeftTitle()
            navigateBack(this@AlbumMusicFragment)
            menu.clear()
            inflateMenu(R.menu.menu_fragment_music)
            title = musicSet.toolbarTitle
            menu.findItem(R.id.menu_add)?.isVisible = musicSet.supportsAddTracks
            setOnMenuItemClickListener(this@AlbumMusicFragment)
        }
        binding.appbarLayout.bringToFront()

        binding.collapsingToolbar.apply {
            setContentScrimColor(0)
            setStatusBarScrimColor(0)
            isTitleEnabled = false
            title = null
        }
    }

    private fun setupHeader(binding: FragmentAlbumMusicBinding) {
        if (isCompactHeader) {
            applyCompactHeader(binding)
        } else {
            applyExpandedHeader(binding)
        }
    }

    private fun setupChildFragment() {
        if (childFragmentManager.findFragmentById(R.id.main_child_fragment_container) != null) {
            return
        }

        childFragmentManager.commit {
            replace(
                R.id.main_child_fragment_container,
                TrackListFragment.newInstance(musicSet)
            )
        }
    }

    private fun bindAppBarToList() {
        val binding = binding ?: return
        val list = childTrackListFragment?.view
            ?.findViewById<RecyclerView>(R.id.recyclerview)
            ?: return
        list.isNestedScrollingEnabled = true
        list.overScrollMode = View.OVER_SCROLL_ALWAYS
        if (list is COUIRecyclerView) {
            list.setOverScrollEnable(true)
        }
        binding.appbarLayout.bindRecyclerView(list)
    }

    private fun applyCompactHeader(binding: FragmentAlbumMusicBinding) {
        val statusBarHeight = requireContext().resolveStatusBarHeightPx()
        binding.collapsingToolbar.updateLayoutParams<AppBarLayout.LayoutParams> {
            height = resources.getDimensionPixelSize(
                com.coui.appcompat.R.dimen.toolbar_min_height
            ) + statusBarHeight
            scrollFlags = AppBarLayout.LayoutParams.SCROLL_FLAG_NO_SCROLL
        }
        binding.musicsetAlbum.visibility = View.GONE
        binding.musicsetAlbum.alpha = 0f
        binding.appbarLayout.setExpanded(true, false)
    }

    private fun applyExpandedHeader(binding: FragmentAlbumMusicBinding) {
        val context = requireContext()
        val shortSide = min(context.screenWidth, context.screenHeight)
        val statusBarHeight = context.resolveStatusBarHeightPx()
        val heroHeight = (shortSide * HEADER_HEIGHT_RATIO).toInt() + statusBarHeight

        binding.collapsingToolbar.updateLayoutParams<AppBarLayout.LayoutParams> {
            height = heroHeight
            scrollFlags = AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL or
                AppBarLayout.LayoutParams.SCROLL_FLAG_EXIT_UNTIL_COLLAPSED or
                AppBarLayout.LayoutParams.SCROLL_FLAG_SNAP
        }

        binding.musicsetAlbum.visibility = View.VISIBLE
        binding.collapsingToolbar.title = musicSet.name
        binding.collapsingToolbar.isTitleEnabled = true

        bindHeaderImage(
            albumImage = binding.musicsetAlbum,
            albumArt = musicSet.albumArt
        )

        bindHeaderCollapseEffect(
            appBarLayout = binding.appbarLayout,
            albumImage = binding.musicsetAlbum
        )
    }

    private fun bindHeaderImage(
        albumImage: MaskImageView,
        albumArt: Any?
    ) {
        albumImage.scaleType = ImageView.ScaleType.CENTER_CROP
        albumImage.alpha = 1f
        albumImage.setMaskColor(HEADER_MASK_COLOR)

        Glide.with(albumImage)
            .load(albumArt)
            .placeholder(headerPlaceholderResId)
            .error(headerPlaceholderResId)
            .centerCrop()
            .into(albumImage)
    }

    private fun bindHeaderCollapseEffect(
        appBarLayout: AppBarLayout,
        albumImage: MaskImageView
    ) {
        appBarLayout.addOnOffsetChangedListener { layout, verticalOffset ->
            val totalScroll = layout.totalScrollRange.toFloat()

            val progress = if (totalScroll > 0f) {
                abs(verticalOffset) / totalScroll
            } else {
                0f
            }

            albumImage.alpha = 1f - progress
        }
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_search -> {
                SearchActivity.start(requireContext())
                true
            }

            R.id.menu_more -> {
                val toolbar = requireBinding().toolbar
                val anchor = toolbar.findViewById<View>(item.itemId) ?: toolbar
                showMoreMenu(anchor)
                true
            }

            R.id.menu_add -> {
                MusicSelectActivity.start(
                    context = requireContext(),
                    musicSet = musicSet
                )
                true
            }

            else -> false
        }
    }

    private fun showMoreMenu(anchor: View) {
        val sortState = childTrackListFragment?.currentSortState() ?: TrackListSortState()
        ContextMenu(
            context = requireContext(),
            musicSet = musicSet,
            theme = themeRepo.getCorePalette(),
            onAction = ::handleMusicSetMenuAction,
            currentSortStyle = sortState.sortStyle,
            currentSortDescending = sortState.sortDescending
        ).show(anchor)
    }

    private fun handleMusicSetMenuAction(action: ContextMenuAction) {
        when (action) {
            ContextMenuAction.Select -> {
                openSelection()
            }

            is ContextMenuAction.SortChanged,
            ContextMenuAction.ShuffleAll,
            ContextMenuAction.PlayNext,
            ContextMenuAction.AddToQueue,
            ContextMenuAction.AddToPlaylist,
            ContextMenuAction.ClearFavorites,
            ContextMenuAction.ClearRecentlyAdded,
            ContextMenuAction.ClearRecentlyPlayed,
            ContextMenuAction.ClearMostPlayed -> {
                childTrackListFragment?.handleContextMenuAction(action)
            }

            ContextMenuAction.Rename -> {
                showRenameDialog()
            }

            ContextMenuAction.ManageArtwork -> {
                showManageArtworkDialog()
            }

            ContextMenuAction.AddToHomeScreen -> {
                val context = requireContext()
                val success = MusicSetShortcutHelper.requestPinnedShortcut(
                    context = context,
                    musicSet = musicSet,
                    title = musicSet.name
                )
                ToastUtil.show(
                    context,
                    if (success) R.string.succeed else R.string.feature_not_implemented
                )
            }

            else -> Unit
        }
    }

    private fun openSelection() {
        MusicEditActivity.start(
            context = requireContext(),
            musicSet = musicSet
        )
    }

    private fun showRenameDialog() {
        when (val set = musicSet) {
            is MusicSet.Playlist -> {
                PlaylistInputDialog
                    .forSet(
                        set = set,
                        mode = PlaylistInputDialog.MODE_RENAME_SET
                    )
                    .show(
                        parentFragmentManager,
                        TAG_RENAME_PLAYLIST_DIALOG
                    )
            }

            is MusicSet.Album,
            is MusicSet.Artist,
            is MusicSet.Genre -> {
                PlaylistInputDialog
                    .forSet(
                        set = set,
                        mode = PlaylistInputDialog.MODE_RENAME_SET
                    )
                    .show(
                        parentFragmentManager,
                        TAG_RENAME_PLAYLIST_DIALOG
                    )
            }

            else -> ToastUtil.show(requireContext(), R.string.feature_not_implemented)
        }
    }

    private fun registerRenameResultListener() {
        parentFragmentManager.setFragmentResultListener(
            PlaylistInputDialog.RESULT_REQUEST_KEY,
            viewLifecycleOwner
        ) { _, bundle ->
            val renamedSet = bundle.parcelable<MusicSet>(PlaylistInputDialog.RESULT_RENAMED_SET)
                ?: return@setFragmentResultListener
            if (renamedSet.id != musicSet.id) return@setFragmentResultListener
            updateMusicSetTitle(renamedSet)
        }
    }

    private fun updateMusicSetTitle(newSet: MusicSet) {
        val previousSet = musicSet
        musicSet = newSet
        val binding = requireBinding()
        binding.toolbar.title = musicSet.toolbarTitle
        if (!isCompactHeader) {
            binding.collapsingToolbar.title = musicSet.name
        }
        if (shouldRecreateTrackListForRename(previousSet, newSet)) {
            childTrackListFragment?.onMusicSetRenamed(newSet)
        }
    }

    private fun shouldRecreateTrackListForRename(oldSet: MusicSet, newSet: MusicSet): Boolean {
        if (oldSet::class != newSet::class) return false
        if (oldSet.name == newSet.name) return false

        return newSet is MusicSet.Album ||
            newSet is MusicSet.Artist ||
            newSet is MusicSet.Genre
    }

    private fun showManageArtworkDialog() {
        ManageArtworkDialogFragment
            .newInstance(
                ArtworkRequest.MusicSetTarget(musicSet)
            )
            .show(
                parentFragmentManager,
                ManageArtworkDialogFragment::class.java.simpleName
            )
    }

    private fun registerArtworkResultListener() {
        parentFragmentManager.setFragmentResultListener(
            ManageArtworkDialogFragment.RESULT_KEY_ARTWORK_APPLIED,
            viewLifecycleOwner
        ) { _, bundle ->
            val request = bundle.parcelable<ArtworkRequest>(ManageArtworkDialogFragment.RESULT_REQUEST)
                as? ArtworkRequest.MusicSetTarget
                ?: return@setFragmentResultListener
            if (!request.musicSet.matchesArtworkTarget(musicSet)) {
                return@setFragmentResultListener
            }

            val artworkPath = bundle.getString(ManageArtworkDialogFragment.RESULT_ARTWORK_PATH)
            musicSet = musicSet.withUpdatedAlbumArt(artworkPath)

            if (!isCompactHeader) {
                bindHeaderImage(
                    albumImage = requireBinding().musicsetAlbum,
                    albumArt = musicSet.albumArt
                )
            }
        }
    }

    private fun MusicSet.matchesArtworkTarget(other: MusicSet): Boolean {
        if (id != other.id) return false
        return when (this) {
            is MusicSet.Folder -> other is MusicSet.Folder && folderPath == other.folderPath
            else -> name == other.name
        }
    }

    private fun MusicSet.withUpdatedAlbumArt(artworkPath: String?): MusicSet {
        return when (this) {
            is MusicSet.Album -> copy(albumArt = artworkPath)
            is MusicSet.Artist -> copy(albumArt = artworkPath)
            is MusicSet.Genre -> copy(albumArt = artworkPath)
            is MusicSet.Playlist -> copy(albumArt = artworkPath, sourcePicture = artworkPath.orEmpty())
            is MusicSet.Folder -> copy(albumArt = artworkPath)
            else -> this
        }
    }

    private fun readMusicSetFromArgs(): MusicSet? {
        return arguments?.parcelable(ARG_MUSIC_SET)
    }

    private val childTrackListFragment: TrackListFragment?
        get() {
            return childFragmentManager
                .findFragmentById(R.id.main_child_fragment_container) as? TrackListFragment
        }

    private val MusicSet.toolbarTitle: String
        get() {
            return when (this) {
                is MusicSet.Favorites -> getString(R.string.favorite)
                is MusicSet.RecentlyPlayed -> getString(R.string.recent_play)
                is MusicSet.RecentlyAdded -> getString(R.string.recent_add)
                is MusicSet.MostPlayed -> getString(R.string.most_play)
                else -> name
            }
        }

    private val MusicSet.supportsAddTracks: Boolean
        get() {
            return this is MusicSet.Playlist || this is MusicSet.Favorites
        }

    private val MusicSet.headerPlaceholderResId: Int
        get() {
            return when (id) {
                MusicSet.ARTISTS -> R.drawable.artist_large
                MusicSet.GENRES -> R.drawable.genre_large
                MusicSet.FOLDERS -> R.drawable.folder_large
                MusicSet.USER_PLAYLIST -> R.drawable.main_list_simple
                else -> {
                    when (this) {
                        is MusicSet.Artist -> R.drawable.artist_large
                        is MusicSet.Genre -> R.drawable.genre_large
                        is MusicSet.Folder -> R.drawable.folder_large
                        is MusicSet.Playlist -> R.drawable.main_list_simple
                        else -> R.drawable.album_large
                    }
                }
            }
        }

    companion object {
        private const val HEADER_MASK_COLOR = 0x33000000
        private const val HEADER_HEIGHT_RATIO = 0.6f
        private const val TAG_RENAME_PLAYLIST_DIALOG = "rename_playlist_dialog"

        fun newInstance(musicSet: MusicSet): AlbumMusicFragment {
            return AlbumMusicFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_MUSIC_SET, musicSet)
                }
            }
        }
    }

    override fun onDestroyView() {
        binding?.appbarLayout?.bindRecyclerView(null)
        super.onDestroyView()
    }
}

