package gd.app.musicplayer.feature.player.queue

import android.os.Bundle
import android.view.LayoutInflater
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.loadCircularArtwork
import gd.app.musicplayer.databinding.FragmentQueueControlBinding
import gd.app.musicplayer.ui.common.base.ViewBindingFragment
import gd.app.musicplayer.feature.player.full.MusicPlayActivity
import gd.app.musicplayer.feature.player.full.PlaybackProgressUiState
import gd.app.musicplayer.feature.player.full.PlayerViewModel
import gd.app.musicplayer.feature.player.full.TrackUiState
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

@AndroidEntryPoint
class QueueControlFragment : ViewBindingFragment<FragmentQueueControlBinding>() {

    private val playerViewModel: PlayerViewModel by viewModels()
    private val queueViewModel: QueueViewModel by viewModels()

    private var isFromMusicPlayActivity = false

    override fun onCreateBinding(inflater: LayoutInflater): FragmentQueueControlBinding =
        FragmentQueueControlBinding.inflate(inflater)

    override fun onBindingCreated(
        binding: FragmentQueueControlBinding,
        savedInstanceState: Bundle?
    ) {
        super.onBindingCreated(binding, savedInstanceState)

        isFromMusicPlayActivity =
            arguments?.getString(ARG_FROM_CLASS) == MusicPlayActivity::class.java.name

        applyInsets(binding)
        setupClickListeners(binding)
        observeTrackMetadata()
        observePlaybackProgress()
        observeQueueState()
    }

    private fun applyInsets(binding: FragmentQueueControlBinding) {
        ViewCompat.setOnApplyWindowInsetsListener(binding.mainBottomControlPanel) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val toolbarHeight =
                resources.getDimensionPixelSize(R.dimen.main_control_banner_height) + systemBars.bottom

            view.updateLayoutParams {
                height = toolbarHeight
            }
            view.updatePadding(bottom = systemBars.bottom)

            insets
        }

        ViewCompat.requestApplyInsets(binding.mainBottomControlPanel)
    }

    private fun setupClickListeners(binding: FragmentQueueControlBinding) = with(binding) {
        mainControlPlayPause.setOnClickListener { onPlayPauseClicked() }
        mainControlLocation.setOnClickListener { scrollToCurrentTrack() }

        val openPlayerClick = { handleMainPanelClick() }
        itemMainControlAlbum.setOnClickListener { openPlayerClick() }
        itemMainControlTitle.setOnClickListener { openPlayerClick() }
        itemMainControlArtist.setOnClickListener { openPlayerClick() }
    }

    private fun observeTrackMetadata() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                playerViewModel.trackUiState.collect { state ->
                    renderTrackMetadata(state)
                }
            }
        }
    }

    private fun observePlaybackProgress() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                playerViewModel.progressUiState.collect { state ->
                    renderPlaybackProgress(state)
                }
            }
        }
    }

    private fun observeQueueState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    queueViewModel.queueState,
                    playerViewModel.trackUiState
                ) { queueState, trackState ->
                    queueState to trackState
                }.collect { (queueState, trackState) ->
                    val binding = requireBinding()
                    val hasQueue = queueState.queue.isNotEmpty()

                    binding.mainControlLocation.isEnabled = hasQueue
                    binding.mainControlLocation.alpha = if (hasQueue) 1f else 0.4f
                    binding.mainControlLocation.isSelected =
                        hasQueue && queueState.currentIndex in queueState.queue.indices

                    if (!hasQueue && trackState.musicId == null) {
                        renderTrackMetadata(TrackUiState())
                    }
                }
            }
        }
    }

    private fun renderTrackMetadata(state: TrackUiState) {
        val binding = requireBinding()

        binding.itemMainControlTitle.text = state.title.ifBlank {
            getString(R.string.music)
        }
        binding.itemMainControlArtist.text = state.artist.ifBlank {
            getString(R.string.artist)
        }
        binding.itemMainControlAlbum.loadCircularArtwork(
            state.artworkSource ?: "",
            R.drawable.notify_default_album_circle
        )
    }

    private fun renderPlaybackProgress(state: PlaybackProgressUiState) {
        val binding = requireBinding()
        val durationMs = state.durationMs.coerceAtLeast(1L)
        val positionMs = state.positionMs.coerceIn(0L, durationMs)

        binding.mainControlPlayPause.isSelected = state.isPlaying
        binding.mainMusicProgress.setMax(durationMs.toInt())
        binding.mainMusicProgress.setProgress(positionMs.toInt())
    }

    private fun onPlayPauseClicked() {
        playerViewModel.onPrimaryPlayPauseClicked(requireContext())
    }

    private fun handleMainPanelClick() {
        if (isFromMusicPlayActivity) {
            requireActivity().finish()
        } else {
            MusicPlayActivity.start(requireContext())
        }
    }

    private fun scrollToCurrentTrack() {
        (parentFragmentManager.findFragmentByTag(PlaybackQueueFragment::class.java.simpleName) as? PlaybackQueueFragment)
            ?.scrollToCurrentTrack()
    }

    companion object {
        private const val ARG_FROM_CLASS = "from_class"

        fun newInstance(fromClass: String): QueueControlFragment {
            return QueueControlFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_FROM_CLASS, fromClass)
                }
            }
        }
    }
}
