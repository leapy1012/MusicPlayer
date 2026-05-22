package gd.app.musicplayer.feature.player.bottomsheet

import android.os.Bundle
import android.view.LayoutInflater
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bumptech.glide.Glide
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.applyRoundedOutline
import gd.app.musicplayer.core.common.extension.loadMusicArtwork
import gd.app.musicplayer.core.common.extension.toDurationString
import gd.app.musicplayer.core.designsystem.view.SeekBar
import gd.app.musicplayer.databinding.FragmentMainControl2Binding
import gd.app.musicplayer.ui.common.base.BasePlayerSheetActivity
import gd.app.musicplayer.ui.common.base.PlaybackQueueBottomSheetFragment
import gd.app.musicplayer.ui.common.base.ViewBindingFragment
import gd.app.musicplayer.feature.player.full.MusicPlayActivity
import gd.app.musicplayer.feature.player.full.PlaybackProgressUiState
import gd.app.musicplayer.feature.player.full.PlayerViewModel
import gd.app.musicplayer.feature.player.full.TrackUiState
import gd.app.musicplayer.ui.shell.MainActivity
import kotlinx.coroutines.launch

@AndroidEntryPoint
class BottomPlayerFragment : ViewBindingFragment<FragmentMainControl2Binding>(),
    SeekBar.OnSeekBarChangeListener {

    private val viewModel: PlayerViewModel by activityViewModels()
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
        observeTrackMetadata()
        observePlaybackProgress()
    }

    private fun observeTrackMetadata() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.trackUiState.collect { state ->
                    renderTrackMetadata(state)
                }
            }
        }
    }

    private fun observePlaybackProgress() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.progressUiState.collect { state -> renderPlaybackProgress(state) }
            }
        }
    }

    private fun renderTrackMetadata(state: TrackUiState) {
        val binding = requireBinding()

        binding.mainControlTitle.text = state.title.ifBlank {
            getString(R.string.music)
        }
        binding.mainControlArtist.text = state.artist.ifBlank {
            getString(R.string.artist)
        }
        binding.mainControlLeft.isSelected = state.isFavorite
        binding.mainControlAlbum.applyRoundedOutline(R.dimen.item_image_corner_radius)
        binding.mainControlAlbum.loadMusicArtwork(
            state.artworkSource ?: R.drawable.default_album_identify
        )
    }

    private fun renderPlaybackProgress(state: PlaybackProgressUiState) {
        val binding = binding ?: return

        val durationMs = state.durationMs
            .coerceAtLeast(1L)
            .coerceAtMost(Int.MAX_VALUE.toLong())

        val positionMs = state.positionMs.coerceIn(0L, durationMs)

        binding.mainControlPlayPause.isSelected = state.isPlaying
        binding.mainControlCurrTime.text = positionMs.toDurationString()
        binding.mainControlTotalTime.text = durationMs.toDurationString()
        binding.mainControlProgress.setMax(durationMs.toInt())

        if (!userSeeking) {
            binding.mainControlProgress.setProgress(positionMs.toInt())
        }
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
        mainControlLeft.setOnClickListener { viewModel.toggleFavorite(requireContext()) }
        mainControlAlbum.setOnClickListener { openPlayerScreen() }
        mainControlTitle.setOnClickListener { openPlayerScreen() }
        mainControlArtist.setOnClickListener { openPlayerScreen() }

        mainControlProgress.setOnSeekBarChangeListener(this@BottomPlayerFragment)
    }

    private fun onPlayPauseClicked() {
        viewModel.onPrimaryPlayPauseClicked(requireContext())
    }

    private fun collapsePlayerPanel() {
        (activity as? BasePlayerSheetActivity)?.collapsePlayerPanel()
    }

    private fun showPlaybackQueue() {
        PlaybackQueueBottomSheetFragment.show(childFragmentManager)
    }

    private fun openPlayerScreen() {
        MusicPlayActivity.start(requireContext())
    }

    override fun onProgressChanged(
        seekBar: SeekBar,
        progress: Int,
        fromUser: Boolean
    ) {
        if (!fromUser) return

        val binding = binding ?: return
        binding.mainControlCurrTime.text = progress.toLong().toDurationString()
    }

    override fun onStopTrackingTouch(seekBar: SeekBar) {
        userSeeking = false
        viewModel.seekTo(requireContext(), seekBar.getProgress())
    }

    override fun onStartTrackingTouch(seekBar: SeekBar) {
        userSeeking = true
    }
}
