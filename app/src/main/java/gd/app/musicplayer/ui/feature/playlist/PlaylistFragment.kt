package gd.app.musicplayer.ui.feature.playlist

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.fueled.draggablerecyclerview.DragItemTouchHelperCallback
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.core.extension.applySystemBarInsets
import gd.app.musicplayer.core.extension.navigateBack
import gd.app.musicplayer.core.util.ToastUtil
import gd.app.musicplayer.data.model.MusicSet
import gd.app.musicplayer.databinding.FragmentAlbumListItemBinding
import gd.app.musicplayer.databinding.FragmentPlaylistBinding
import gd.app.musicplayer.ui.feature.library.AlbumMusicActivity
import gd.app.musicplayer.ui.feature.library.ListMoreMenuHost
import gd.app.musicplayer.ui.feature.library.MusicSetOptionsDialog
import gd.app.musicplayer.ui.feature.search.SearchActivity
import gd.app.musicplayer.ui.feature.selection.MusicSetEditActivity
import gd.app.musicplayer.ui.common.base.ViewBindingFragment
import gd.app.musicplayer.ui.common.base.WrapContentLinearLayoutManager
import gd.app.musicplayer.ui.common.model.resolvePlaceholderRes
import gd.app.musicplayer.ui.common.menu.MusicSetContextMenu
import gd.app.musicplayer.ui.common.menu.MusicSetMenuAction
import gd.app.musicplayer.ui.theme.applyCurrentTheme
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PlaylistFragment :
    ViewBindingFragment<FragmentPlaylistBinding>(),
    ListMoreMenuHost {

    private val viewModel: PlaylistViewModel by viewModels()

    private val playlistAdapter by lazy(LazyThreadSafetyMode.NONE) {
        PlaylistAdapter(
            onPlaylistClick = ::openPlaylist,
            onPlaylistMenuClick = ::showPlaylistItemMenu,
            onPlaylistOrderChanged = viewModel::updatePlaylistOrder
        )
    }

    private val playlistLayoutManager by lazy(LazyThreadSafetyMode.NONE) {
        WrapContentLinearLayoutManager(
            requireContext(),
            RecyclerView.VERTICAL,
            false
        )
    }

    override fun onCreateBinding(
        inflater: LayoutInflater
    ): FragmentPlaylistBinding {
        return FragmentPlaylistBinding.inflate(inflater)
    }

    override fun onBindingCreated(
        binding: FragmentPlaylistBinding,
        savedInstanceState: Bundle?
    ) {
        super.onBindingCreated(binding, savedInstanceState)

        setupInsets(binding)
        setupToolbar(binding)
        setupRecyclerView(binding)

        observeUiState()
        observeEvents()
    }

    private fun setupInsets(binding: FragmentPlaylistBinding) = with(binding) {
        root.applySystemBarInsets(
            statusBarSpace,
            root
        )
    }

    private fun setupToolbar(binding: FragmentPlaylistBinding) = with(binding.toolbar) {
        title = getString(R.string.playlist).uppercase()

        inflateMenu(R.menu.menu_fragment_playlist)
        navigateBack(this@PlaylistFragment)
        setOnMenuItemClickListener(::onMenuItemClick)
    }

    private fun setupRecyclerView(binding: FragmentPlaylistBinding) {
        with(binding.recyclerview.recyclerview) {
            layoutManager = playlistLayoutManager
            setHasFixedSize(true)
            adapter = playlistAdapter

            attachDragHelper()

            if (itemDecorationCount == 0) {
                addItemDecoration(
                    PlaylistItemDecoration(requireContext())
                )
            }
        }
    }

    private fun RecyclerView.attachDragHelper() {
        ItemTouchHelper(
            DragItemTouchHelperCallback.Builder(
                ItemTouchHelper.UP or ItemTouchHelper.DOWN,
                0
            )
                .setDragEnabled(true)
                .onItemDragListener(playlistAdapter::onItemDragged)
                .build()
        ).attachToRecyclerView(this)
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    private fun observeEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect(::handleEvent)
            }
        }
    }

    private fun render(state: PlaylistUiState) {
        playlistAdapter.submitList(state.playlists)

        requireBinding().recyclerview.recyclerview.isVisible =
            state.playlists.isNotEmpty()
    }

    private fun handleEvent(event: PlaylistEvent) {
        when (event) {
            is PlaylistEvent.ShowMessage -> {
                ToastUtil.show(
                    requireContext(),
                    event.messageRes
                )
            }
        }
    }

    private fun onMenuItemClick(item: MenuItem): Boolean {
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

            else -> false
        }
    }

    override fun showMoreMenu(anchor: View) {
        MusicSetContextMenu(
            context = requireContext(),
            musicSet = MusicSet.Playlists,
            onAction = ::handlePlaylistMenuAction
        ).show(anchor)
    }

    private fun handlePlaylistMenuAction(action: MusicSetMenuAction) {
        when (action) {
            MusicSetMenuAction.Select -> {
                openPlaylistSelection()
            }

            MusicSetMenuAction.BackupPlaylists,
            MusicSetMenuAction.RestorePlaylists,
            MusicSetMenuAction.DeleteEmptyPlaylists -> {
                viewModel.onMenuAction(action)
            }

            else -> Unit
        }
    }

    private fun openPlaylistSelection() {
        MusicSetEditActivity.start(
            context = requireContext(),
            musicSet = MusicSet.Playlists,
            visibleItems = playlistAdapter.currentItems()
        )
    }

    private fun openPlaylist(playlist: MusicSet.Playlist) {
        AlbumMusicActivity.start(
            context = requireContext(),
            musicSet = playlist
        )
    }

    private fun showPlaylistItemMenu(
        playlist: MusicSet.Playlist,
        anchor: View
    ) {
        MusicSetOptionsDialog
            .newInstance(playlist)
            .show(
                parentFragmentManager,
                MusicSetOptionsDialog::class.java.simpleName
            )
    }

    private class PlaylistAdapter(
        private val onPlaylistClick: (MusicSet.Playlist) -> Unit,
        private val onPlaylistMenuClick: (MusicSet.Playlist, View) -> Unit,
        private val onPlaylistOrderChanged: (List<Long>) -> Unit
    ) : RecyclerView.Adapter<PlaylistAdapter.ViewHolder>() {

        private val playlists = mutableListOf<MusicSet.Playlist>()

        init {
            setHasStableIds(true)
        }

        override fun getItemCount(): Int {
            return playlists.size
        }

        override fun getItemId(position: Int): Long {
            return playlists[position].id
        }

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int
        ): ViewHolder {
            val binding = FragmentAlbumListItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )

            applyCurrentTheme(binding.root)

            return ViewHolder(
                binding = binding,
                onPlaylistClick = onPlaylistClick,
                onPlaylistMenuClick = onPlaylistMenuClick
            )
        }

        override fun onBindViewHolder(
            holder: ViewHolder,
            position: Int
        ) {
            holder.bind(playlists[position])
        }

        fun submitList(items: List<MusicSet.Playlist>) {
            if (playlists == items) return

            playlists.clear()
            playlists.addAll(items)

            notifyDataSetChanged()
        }

        fun currentItems(): List<MusicSet> {
            return playlists.toList()
        }

        fun onItemDragged(
            fromPosition: Int,
            toPosition: Int
        ) {
            if (!canMoveItem(fromPosition, toPosition)) {
                return
            }

            val movedPlaylist = playlists.removeAt(fromPosition)
            playlists.add(toPosition, movedPlaylist)

            notifyItemMoved(
                fromPosition,
                toPosition
            )

            onPlaylistOrderChanged(
                playlists.map(MusicSet.Playlist::id)
            )
        }

        private fun canMoveItem(
            fromPosition: Int,
            toPosition: Int
        ): Boolean {
            return fromPosition in playlists.indices &&
                    toPosition in playlists.indices &&
                    fromPosition != toPosition
        }

        class ViewHolder(
            private val binding: FragmentAlbumListItemBinding,
            private val onPlaylistClick: (MusicSet.Playlist) -> Unit,
            private val onPlaylistMenuClick: (MusicSet.Playlist, View) -> Unit
        ) : RecyclerView.ViewHolder(binding.root) {

            private var currentItem: MusicSet.Playlist? = null

            init {
                binding.root.setOnClickListener {
                    currentItem?.let(onPlaylistClick)
                }

                binding.musicItemMenu.setOnClickListener { anchor ->
                    currentItem?.let { playlist ->
                        onPlaylistMenuClick(
                            playlist,
                            anchor
                        )
                    }
                }
            }

            fun bind(playlist: MusicSet.Playlist) {
                currentItem = playlist

                val resources = binding.root.resources

                binding.musicItemTitle.text = playlist.name

                binding.musicItemArtist.text = resources.getQuantityString(
                    R.plurals.plurals_track,
                    playlist.musicCount,
                    playlist.musicCount
                )

                binding.musicItemAlbum.setImageResource(
                    playlist.resolvePlaceholderRes(false)
                )
            }
        }
    }

    companion object {
        fun newInstance(): PlaylistFragment {
            return PlaylistFragment()
        }
    }
}
