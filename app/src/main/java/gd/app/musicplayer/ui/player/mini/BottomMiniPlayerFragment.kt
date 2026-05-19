package gd.app.musicplayer.ui.player.mini

import android.os.Bundle
import android.view.LayoutInflater
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.loadCircularArtwork
import gd.app.musicplayer.databinding.MainBottomControlPanelBinding
import gd.app.musicplayer.ui.common.base.PlaybackQueueBottomSheetFragment
import gd.app.musicplayer.ui.common.base.ViewBindingFragment
import gd.app.musicplayer.ui.player.full.MusicPlayActivity
import gd.app.musicplayer.ui.player.full.PlaybackProgressUiState
import gd.app.musicplayer.ui.player.full.PlayerViewModel
import gd.app.musicplayer.ui.player.full.TrackUiState
import kotlinx.coroutines.launch

@AndroidEntryPoint
class BottomMiniPlayerFragment : ViewBindingFragment<MainBottomControlPanelBinding>() {

    private val playerViewModel: PlayerViewModel by activityViewModels()

    private val shouldApplyInsets: Boolean
        get() = arguments?.getBoolean(ARG_APPLY_INSETS, true) ?: true

    override fun onCreateBinding(inflater: LayoutInflater): MainBottomControlPanelBinding {
        return MainBottomControlPanelBinding.inflate(inflater)
    }

    override fun onBindingCreated(
        binding: MainBottomControlPanelBinding,
        savedInstanceState: Bundle?
    ) {
        super.onBindingCreated(binding, savedInstanceState)

        if (shouldApplyInsets) {
            setupInsets(binding)
        }
        setupControls(binding)
        observeTrackMetadata()
        observePlaybackProgress()
    }

    private fun setupInsets(binding: MainBottomControlPanelBinding) {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bottomInset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            view.updatePadding(bottom = bottomInset)
            insets
        }

        ViewCompat.requestApplyInsets(binding.root)
    }

    private fun setupControls(binding: MainBottomControlPanelBinding) = with(binding) {
        mainControlPlayPause.setOnClickListener { onPlayPauseClicked() }
        mainControlNext.setOnClickListener { playerViewModel.playNext(requireContext()) }
        mainControlList.setOnClickListener { showPlaybackQueue() }
        root.setOnClickListener {openPlayer()}
        itemMainControlAlbum.setOnClickListener { openPlayer() }
        itemMainControlArtist.setOnClickListener { openPlayer() }
        itemMainControlTitle.setOnClickListener { openPlayer() }
    }

    /**
     * Queue/current-index updates drive the pager only.
     * Progress ticks should not rebuild pager pages.
     */
    private fun observeTrackMetadata() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                playerViewModel.trackUiState.collect { state ->
                    renderTrackMetadata(state)
                }
            }
        }
    }

    /**
     * Progress updates frequently. Keep this cheap.
     */
    private fun observePlaybackProgress() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                playerViewModel.progressUiState.collect { state ->
                    renderPlaybackProgress(state)
                }
            }
        }
    }


    private fun renderPlaybackProgress(state: PlaybackProgressUiState) {
        val binding = binding ?: return

        binding.mainControlPlayPause.isSelected = state.isPlaying

        if (state.durationMs <= 0L) {
            binding.mainMusicProgress.setProgress(0)
            return
        }

        val durationMs = state.durationMs.coerceAtMost(Int.MAX_VALUE.toLong())
        val progressMs = state.positionMs.coerceIn(0L, durationMs)

        binding.mainMusicProgress.setMax(durationMs.toInt())
        binding.mainMusicProgress.setProgress(progressMs.toInt())
    }

    private fun renderTrackMetadata(state: TrackUiState) {
        val binding = binding ?: return

        binding.itemMainControlTitle.text = state.title.ifBlank {
            getString(R.string.music)
        }

        binding.itemMainControlArtist.text = state.artist.ifBlank {
            getString(R.string.artist)
        }

        binding.itemMainControlAlbum.loadCircularArtwork(
            state.artworkSource ?: R.drawable.notify_default_album_circle,
            R.drawable.notify_default_album_circle
        )
    }

    private fun onPlayPauseClicked() {
        playerViewModel.onPrimaryPlayPauseClicked(requireContext())
    }

    private fun showPlaybackQueue() {
        PlaybackQueueBottomSheetFragment.show(childFragmentManager)
    }

    private fun openPlayer() {
        MusicPlayActivity.start(requireContext())
    }

    companion object {
        private const val ARG_APPLY_INSETS = "apply_insets"

        fun newInstance(applyInsets: Boolean = true): BottomMiniPlayerFragment {
            return BottomMiniPlayerFragment().apply {
                arguments = Bundle().apply {
                    putBoolean(ARG_APPLY_INSETS, applyInsets)
                }
            }
        }
    }
}
