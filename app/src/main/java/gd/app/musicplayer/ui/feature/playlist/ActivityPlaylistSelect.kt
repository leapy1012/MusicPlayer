package gd.app.musicplayer.ui.feature.playlist

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.data.model.MusicSet
import gd.app.musicplayer.ui.common.base.BaseActivity
import gd.app.musicplayer.ui.common.base.WrapContentLinearLayoutManager
import gd.app.musicplayer.ui.common.base.setupEdgeToEdgeToolbar
import gd.app.musicplayer.core.util.ToastUtil
import gd.app.musicplayer.core.extension.parcelableArrayList
import gd.app.musicplayer.core.extension.startActivityCompat
import gd.app.musicplayer.databinding.ActivityPlaylistSelectBinding
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ActivityPlaylistSelect : BaseActivity() {

    private val viewModel: PlaylistSelectViewModel by viewModels()

    private lateinit var binding: ActivityPlaylistSelectBinding
    private lateinit var songs: List<Music>

    private val adapter by lazy { buildAdapter() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        songs = intent.extras?.parcelableArrayList<Music>(ARG_SONGS) ?: arrayListOf()
        if (songs.isEmpty()) {
            finish()
            return
        }

        binding = ActivityPlaylistSelectBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initViews()
        viewModel.setSongs(songs)
        setupCreatePlaylistResult()
        observeViewModel()
    }


    private fun initViews() {
        setupEdgeToEdgeToolbar(
            root = binding.root,
            statusBarView = binding.statusBarSpace,
            bottomPaddingView = binding.root,
            toolbar = binding.toolbar,
            titleRes = R.string.add_to_list
        )

        binding.mainFragmentContainer.recyclerview.layoutManager =
            WrapContentLinearLayoutManager(this, RecyclerView.VERTICAL, false)
        binding.mainFragmentContainer.recyclerview.adapter = adapter

        binding.addToList.setOnClickListener { viewModel.confirmAddToSelectedPlaylists() }

    }

    private fun buildAdapter(): PlaylistSelectAdapter =
        PlaylistSelectAdapter(
            inflater = layoutInflater,
            accentColor = themeRepo.getAccentColor()
        ).apply {
            setOnCreatePlaylistClickListener(::showCreatePlaylistDialog)
            setOnSelectionCountChangedListener {
            }
            setOnSelectionChangedListener { selectedItems ->
                viewModel.updateSelectedPlaylistIds(
                    selectedItems.mapNotNullTo(linkedSetOf()) { item ->
                        (item as? MusicSet.Playlist)?.id
                    }
                )
            }
        }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        adapter.submitPlaylists(
                            items = state.playlists,
                            selectedItems = state.playlists.filterTo(linkedSetOf()) {
                                it.id in state.selectedPlaylistIds
                            }
                        )
                        binding.addToList.visibility = if (state.canConfirm) View.VISIBLE else View.GONE
                    }
                }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            is PlaylistSelectEvent.ShowToast -> ToastUtil.show(this@ActivityPlaylistSelect, event.messageRes)
                            PlaylistSelectEvent.Finish -> finish()
                        }
                    }
                }
            }
        }
    }

    private fun showCreatePlaylistDialog() {
        PlaylistInputDialog.forTracks(
            tracks = songs,
            mode = PlaylistInputDialog.MODE_CREATE_AND_RETURN
        ).show(supportFragmentManager, TAG_CREATE_PLAYLIST_DIALOG)
    }

    private fun setupCreatePlaylistResult() {
        supportFragmentManager.setFragmentResultListener(
            PlaylistInputDialog.RESULT_REQUEST_KEY,
            this
        ) { _, result ->
            val playlistId = result.getLong(PlaylistInputDialog.RESULT_PLAYLIST_ID, -1L)
            if (playlistId > 0L) {
                viewModel.addToCreatedPlaylist(playlistId)
            }
        }
    }

    companion object {
        private const val ARG_SONGS = "songs"
        private const val TAG_CREATE_PLAYLIST_DIALOG = "create_playlist_dialog"

        fun start(context: Context, songs: List<Music>) {
            context.startActivityCompat(Intent(context, ActivityPlaylistSelect::class.java).apply {
                putParcelableArrayListExtra(ARG_SONGS, ArrayList(songs))
            })
        }
    }
}
