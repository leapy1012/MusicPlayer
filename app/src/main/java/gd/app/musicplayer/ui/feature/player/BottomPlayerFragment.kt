package gd.app.musicplayer.ui.feature.player

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.core.extension.isFavorite
import gd.app.musicplayer.core.extension.loadMusicArtwork
import gd.app.musicplayer.core.ui.view.SeekBar
import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.databinding.FragmentMainControl2Binding
import gd.app.musicplayer.domain.usecase.playlist.ToggleFavoriteTrackUseCase
import gd.app.musicplayer.ui.common.base.PlaybackQueueBottomSheetFragment
import gd.app.musicplayer.ui.common.base.ViewBindingFragment
import gd.app.musicplayer.playback.PlaybackControlViewModel
import gd.app.musicplayer.ui.shell.MainActivity
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BottomPlayerFragment : ViewBindingFragment<FragmentMainControl2Binding>(),
    SeekBar.OnSeekBarChangeListener {

    private val viewModel: PlaybackControlViewModel by activityViewModels()

    @Inject
    lateinit var toggleFavoriteTrack: ToggleFavoriteTrackUseCase

    private var currentTrack: Music? = null
    private var currentTrackId: Long? = null
    private var favoriteOverride: Boolean? = null
    private var userSeeking = false

    override fun onCreateBinding(inflater: LayoutInflater): FragmentMainControl2Binding {
        return FragmentMainControl2Binding.inflate(inflater)
    }

    override fun onBindingCreated(
        binding: FragmentMainControl2Binding,
        savedInstanceState: Bundle?
    ) {
        super.onBindingCreated(binding, savedInstanceState)

        setupInsets(binding)
        setupClickListeners(binding)
        setupSeekBar(binding)
        observePlaybackState()
    }

    private fun setupInsets(binding: FragmentMainControl2Binding) {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bottomInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom

            view.updatePadding(bottom = bottomInset)

            insets
        }

        ViewCompat.requestApplyInsets(binding.root)
    }

    private fun setupClickListeners(binding: FragmentMainControl2Binding) = with(binding) {
        mainControlPlayPause.setOnClickListener { onPlayPauseClicked() }
        mainControlPrevious.setOnClickListener { viewModel.playPrevious(requireContext()) }
        mainControlNext.setOnClickListener { viewModel.playNext(requireContext()) }
        mainControlBack.setOnClickListener { collapsePlayerPanel() }
        mainControlRight.setOnClickListener { showPlaybackQueue() }
        mainControlLeft.setOnClickListener { toggleFavorite() }

        val openPlayerClickListener = View.OnClickListener {
            openPlayQueue()
        }

        mainControlAlbum.setOnClickListener(openPlayerClickListener)
        mainControlTitle.setOnClickListener(openPlayerClickListener)
        mainControlArtist.setOnClickListener(openPlayerClickListener)
    }

    private fun setupSeekBar(binding: FragmentMainControl2Binding) {
        binding.mainControlProgress.setOnSeekBarChangeListener(this)
    }

    private fun observePlaybackState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.playbackState.collect { state ->
                    val binding = binding ?: return@collect
                    val track = state.currentTrack

                    updateCurrentTrack(track)

                    if (track == null) {
                        renderEmptyState(binding)
                    } else {
                        renderTrackState(
                            binding = binding,
                            track = track,
                            positionMs = state.positionMs,
                            durationMs = state.durationMs,
                            isPlaying = state.isPlaying
                        )
                    }
                }
            }
        }
    }

    private fun updateCurrentTrack(track: Music?) {
        val newTrackId = track?.id

        if (currentTrackId != newTrackId) {
            favoriteOverride = null
            currentTrackId = newTrackId
        }

        currentTrack = track
    }

    private fun renderEmptyState(binding: FragmentMainControl2Binding) = with(binding) {
        mainControlTitle.text = getString(R.string.music)
        mainControlArtist.text = getString(R.string.artist)

        mainControlCurrTime.text = viewModel.formatTime(0)
        mainControlTotalTime.text = viewModel.formatTime(0)

        mainControlProgress.setMax(DEFAULT_PROGRESS_MAX)
        mainControlProgress.setProgress(0)
        mainControlProgress.isEnabled = false

        mainControlPlayPause.isSelected = false
        mainControlLeft.isSelected = false

        currentTrack = null
        currentTrackId = null
        favoriteOverride = null
    }

    private fun renderTrackState(
        binding: FragmentMainControl2Binding,
        track: Music,
        positionMs: Int,
        durationMs: Int,
        isPlaying: Boolean
    ) = with(binding) {
        val resolvedDurationMs = track.duration.coerceAtLeast(durationMs).coerceAtLeast(1)
        val resolvedFavorite = favoriteOverride ?: track.isFavorite()

        mainControlTitle.text = track.title
        mainControlArtist.text = track.artist.ifBlank {
            getString(R.string.artist)
        }

        mainControlCurrTime.text = viewModel.formatTime(positionMs)
        mainControlTotalTime.text = viewModel.formatTime(resolvedDurationMs)

        mainControlProgress.setMax(resolvedDurationMs)
        mainControlProgress.isEnabled = !track.data.isNullOrBlank()

        if (!userSeeking) {
            mainControlProgress.setProgress(positionMs.coerceAtLeast(0))
        }

        mainControlPlayPause.isSelected = isPlaying
        mainControlLeft.isSelected = resolvedFavorite

        track.loadMusicArtwork(mainControlAlbum)
    }

    private fun onPlayPauseClicked() {
        val state = viewModel.playbackState.value

        if (state.queue.isEmpty()) {
            viewLifecycleOwner.lifecycleScope.launch {
                viewModel.playAllTracks(requireContext())
            }
        } else {
            viewModel.togglePlayPause(requireContext())
        }
    }

    private fun toggleFavorite() {
        val track = currentTrack ?: return

        viewLifecycleOwner.lifecycleScope.launch {
            val isFavorite = toggleFavoriteTrack(track.id)

            favoriteOverride = isFavorite

            binding?.mainControlLeft?.isSelected = isFavorite
        }
    }

    private fun collapsePlayerPanel() {
        (activity as? MainActivity)?.collapsePlayerPanel()
    }

    private fun showPlaybackQueue() {
        PlaybackQueueBottomSheetFragment.show(childFragmentManager)
    }

    private fun openPlayQueue() {
        MusicPlayActivity.start(requireContext())
    }

    override fun onProgressChanged(
        seekBar: SeekBar,
        progress: Int,
        fromUser: Boolean
    ) {
        if (!fromUser) return

        binding?.mainControlCurrTime?.text = viewModel.formatTime(progress)
        viewModel.seekTo(requireContext(), progress)
    }

    override fun onStartTrackingTouch(seekBar: SeekBar) {
        userSeeking = true
    }

    override fun onStopTrackingTouch(seekBar: SeekBar) {
        userSeeking = false
    }

    private companion object {
        private const val DEFAULT_PROGRESS_MAX = 100
    }
}
