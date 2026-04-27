package gd.app.musicplayer.feature.library

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
import com.bumptech.glide.Glide
import com.google.android.material.appbar.AppBarLayout
import dagger.hilt.android.AndroidEntryPoint
import gd.app.lib.view.MaskImageView
import gd.app.musicplayer.R
import gd.app.musicplayer.data.model.MusicSet
import gd.app.musicplayer.databinding.FragmentAlbumMusicBinding
import gd.app.musicplayer.ui.common.base.ViewBindingFragment
import gd.app.musicplayer.ui.feature.menu.MusicSetContextMenu
import gd.app.musicplayer.ui.feature.menu.supportsCompactAlbumHeader
import gd.app.musicplayer.feature.search.SearchActivity
import gd.app.musicplayer.feature.selection.ActivityMusicSelect
import gd.app.musicplayer.core.ui.extension.navigateBack
import gd.app.musicplayer.core.ui.extension.parcelable
import gd.app.musicplayer.core.ui.extension.screenHeight
import gd.app.musicplayer.core.ui.extension.screenWidth
import kotlin.math.abs
import kotlin.math.min

@AndroidEntryPoint
class AlbumMusicFragment : ViewBindingFragment<FragmentAlbumMusicBinding>(),
    Toolbar.OnMenuItemClickListener {

    private lateinit var musicSet: MusicSet
    private val isCompactHeader: Boolean
        get() = musicSet.supportsCompactAlbumHeader
    private val headerPlaceholderResId: Int
        get() = musicSet.headerPlaceholderResId

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        musicSet = readMusicSetFromArgs() ?: run {
            throw IllegalStateException("AlbumMusicFragment requires MusicSet argument")
        }
    }

    override fun onCreateBinding(inflater: LayoutInflater): FragmentAlbumMusicBinding =
        FragmentAlbumMusicBinding.inflate(inflater)

    override fun onBindingCreated(binding: FragmentAlbumMusicBinding, savedInstanceState: Bundle?) {
        super.onBindingCreated(binding, savedInstanceState)

        applyInsets()
        setupToolbar()
        setupHeader()
        setupChildFragment()
    }

    private fun applyInsets() {
        val binding = requireBinding()
        val toolbarBaseHeight = resources.getDimensionPixelSize(R.dimen.common_title_height)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val toolbarHeight = toolbarBaseHeight + systemBars.top
            binding.toolbar.updateLayoutParams {
                height = toolbarHeight
            }
            binding.toolbar.updatePadding(top = systemBars.top)
            if (isCompactHeader) {
                binding.collapsingToolbar.updateLayoutParams {
                    height = toolbarHeight
                }
            }
            binding.root.updatePadding(bottom = systemBars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
    }

    private fun setupToolbar() {
        val binding = requireBinding()
        binding.toolbar.apply {
            navigateBack(this@AlbumMusicFragment)
            title = when (musicSet) {
                is MusicSet.Favorites -> {
                    getString(R.string.favorite).uppercase()
                }

                is MusicSet.RecentlyPlayed -> {
                    getString(R.string.recent_play).uppercase()
                }

                is MusicSet.RecentlyAdded -> {
                    getString(R.string.recent_add).uppercase()
                }

                is MusicSet.MostPlayed -> {
                    getString(R.string.most_play).uppercase()
                }

                else -> {
                    musicSet.name
                }
            }

            menu.findItem(R.id.menu_add)?.isVisible = musicSet is MusicSet.Playlist || musicSet is MusicSet.Favorites
            setOnMenuItemClickListener(this@AlbumMusicFragment)
        }

        binding.collapsingToolbar.apply {
            setContentScrimColor(0)
            setStatusBarScrimColor(0)
            isTitleEnabled = !isCompactHeader
            title = if (isCompactHeader) null else musicSet.name
        }
    }

    private fun setupHeader() {
        if (isCompactHeader) {
            applyCompactHeader(requireBinding())
        } else {
            applyExpandedHeader(requireBinding())
        }
    }

    private fun setupChildFragment() {
        if (childFragmentManager.findFragmentById(R.id.main_child_fragment_container) == null) {
            childFragmentManager.beginTransaction()
                .replace(
                    R.id.main_child_fragment_container,
                    MusicListFragment.newInstance(musicSet)
                )
                .commit()
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
        val shortSide = min(context.screenWidth,context.screenHeight)
        val heroHeight = (shortSide * 0.6f).toInt()

        binding.collapsingToolbar.updateLayoutParams<AppBarLayout.LayoutParams> {
            height = heroHeight
        }
        binding.collapsingToolbar.title = musicSet.name

        bindHeaderImage(binding.musicsetAlbum, musicSet.albumArt)
        bindHeaderCollapseEffect(binding.appbarLayout, binding.musicsetAlbum)
    }

    private fun bindHeaderImage(albumImage: MaskImageView, albumArt: Any?) {
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

    private fun bindHeaderCollapseEffect(appBarLayout: AppBarLayout, albumImage: MaskImageView) {
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

    private fun readMusicSetFromArgs(): MusicSet? {
        return arguments?.parcelable(ARG_MUSIC_SET)
    }

    private val MusicSet.headerPlaceholderResId: Int
        get() = when (id) {
            MusicSet.ARTISTS_ID -> R.drawable.artist_large
            MusicSet.GENRES_ID -> R.drawable.genre_large
            MusicSet.FOLDERS_ID -> R.drawable.folder_large
            MusicSet.PLAYLISTS_ID -> R.drawable.main_list_simple
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

    override fun onMenuItemClick(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_search -> {
                SearchActivity.Companion.start(requireContext())
                true
            }
            R.id.menu_more -> {
                showMoreMenu(requireBinding().root.findViewById(item.itemId))
                true
            }
            R.id.menu_add -> {
                ActivityMusicSelect.Companion.start(requireContext(), musicSet)
                true
            }
            else -> false
        }
    }

    fun showMoreMenu(anchor: View) {
        MusicSetContextMenu(
            context = requireContext(),
            musicSet = musicSet,
            tracksProvider = {
                (childFragmentManager.findFragmentById(R.id.main_child_fragment_container) as? MusicListFragment)
                    ?.getCurrentTracks()
                    .orEmpty()
            },
            onSortChanged = null
        ).show(anchor)
    }

    companion object {
        private const val HEADER_MASK_COLOR = 0x33000000

        fun newInstance(musicSet: MusicSet): AlbumMusicFragment {
            return AlbumMusicFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_MUSIC_SET, musicSet)
                }
            }
        }
    }
}
