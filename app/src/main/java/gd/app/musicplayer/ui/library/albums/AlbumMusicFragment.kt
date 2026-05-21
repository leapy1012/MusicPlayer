package gd.app.musicplayer.ui.library.albums

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.commit
import com.bumptech.glide.Glide
import com.google.android.material.appbar.AppBarLayout
import dagger.hilt.android.AndroidEntryPoint
import gd.app.lib.view.MaskImageView
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.navigateBack
import gd.app.musicplayer.core.common.extension.parcelable
import gd.app.musicplayer.core.common.extension.screenHeight
import gd.app.musicplayer.core.common.extension.screenWidth
import gd.app.musicplayer.core.common.extension.supportsCompactAlbumHeader
import gd.app.musicplayer.core.common.util.ToastUtil
import gd.app.musicplayer.domain.model.ArtworkRequest
import gd.app.musicplayer.domain.model.MusicSet
import gd.app.musicplayer.domain.repository.ThemeRepo
import gd.app.musicplayer.databinding.FragmentAlbumMusicBinding
import gd.app.musicplayer.ui.playlist.PlaylistInputDialog
import gd.app.musicplayer.ui.search.SearchActivity
import gd.app.musicplayer.ui.selection.MusicSelectActivity
import gd.app.musicplayer.ui.selection.MusicEditActivity
import gd.app.musicplayer.ui.shortcut.MusicSetShortcutHelper
import gd.app.musicplayer.ui.common.base.ViewBindingFragment
import gd.app.musicplayer.ui.common.menu.ContextMenu
import gd.app.musicplayer.ui.common.menu.ContextMenuAction
import gd.app.musicplayer.ui.library.ARG_MUSIC_SET
import gd.app.musicplayer.ui.library.artwork.ManageArtworkDialogFragment
import gd.app.musicplayer.ui.library.tracks.TrackListFragment
import gd.app.musicplayer.ui.library.tracks.TrackListSortState
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.min

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

        applyInsets(binding)
        setupToolbar(binding)
        setupHeader(binding)
        registerArtworkResultListener()
        setupChildFragment()
    }

    private fun applyInsets(binding: FragmentAlbumMusicBinding) {
        val toolbarBaseHeight =
            resources.getDimensionPixelSize(R.dimen.common_title_height)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val toolbarHeight = toolbarBaseHeight + systemBars.top

            binding.toolbar.updateLayoutParams {
                height = toolbarHeight
            }

            binding.toolbar.updatePadding(
                top = systemBars.top
            )

            if (isCompactHeader) {
                binding.collapsingToolbar.updateLayoutParams {
                    height = toolbarHeight
                }
            }

            binding.root.updatePadding(
                bottom = systemBars.bottom
            )

            insets
        }

        ViewCompat.requestApplyInsets(binding.root)
    }

    private fun setupToolbar(binding: FragmentAlbumMusicBinding) {
        binding.toolbar.apply {
            navigateBack(this@AlbumMusicFragment)
            title = musicSet.toolbarTitle
            menu.findItem(R.id.menu_add)?.isVisible = musicSet.supportsAddTracks
            setOnMenuItemClickListener(this@AlbumMusicFragment)
        }

        binding.collapsingToolbar.apply {
            setContentScrimColor(0)
            setStatusBarScrimColor(0)
            isTitleEnabled = !isCompactHeader
            title = if (isCompactHeader) null else musicSet.name
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

    private fun applyCompactHeader(binding: FragmentAlbumMusicBinding) {
        binding.appbarLayout.setExpanded(false, false)

        binding.collapsingToolbar.updateLayoutParams {
            height = resources.getDimensionPixelSize(R.dimen.common_title_height)
        }

        binding.musicsetAlbum.alpha = 0f
    }

    private fun applyExpandedHeader(binding: FragmentAlbumMusicBinding) {
        val context = requireContext()
        val shortSide = min(context.screenWidth, context.screenHeight)
        val heroHeight = (shortSide * HEADER_HEIGHT_RATIO).toInt()

        binding.collapsingToolbar.updateLayoutParams<AppBarLayout.LayoutParams> {
            height = heroHeight
        }

        binding.collapsingToolbar.title = musicSet.name

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
                val anchor = requireBinding()
                    .toolbar
                    .findViewById<View>(item.itemId)

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
                ToastUtil.show(requireContext(), R.string.feature_not_implemented)
            }

            else -> {
                ToastUtil.show(requireContext(), R.string.feature_not_implemented)
            }
        }
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
                is MusicSet.Favorites -> getString(R.string.favorite).uppercase()
                is MusicSet.RecentlyPlayed -> getString(R.string.recent_play).uppercase()
                is MusicSet.RecentlyAdded -> getString(R.string.recent_add).uppercase()
                is MusicSet.MostPlayed -> getString(R.string.most_play).uppercase()
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
}

