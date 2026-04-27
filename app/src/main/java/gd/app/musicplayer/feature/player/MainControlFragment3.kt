package gd.app.musicplayer.feature.player

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
import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.data.model.isFavorite
import gd.app.musicplayer.data.model.loadMusicArtwork
import gd.app.musicplayer.databinding.FragmentMainControl2Binding
import gd.app.musicplayer.ui.common.base.PlaybackQueueBottomSheetFragment
import gd.app.musicplayer.ui.common.base.ViewBindingFragment
import gd.app.musicplayer.ui.common.playback.PlaybackControlViewModel
import gd.app.musicplayer.ui.common.view.SeekBar
import gd.app.musicplayer.feature.shell.MainActivity
import gd.app.musicplayer.core.ui.extension.appContainer
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainControlFragment3 : ViewBindingFragment<FragmentMainControl2Binding>(),
    View.OnClickListener,
    SeekBar.OnSeekBarChangeListener {

    private val viewModel: PlaybackControlViewModel by activityViewModels()
    private val toggleFavoriteTrack by lazy { requireContext().appContainer.toggleFavoriteTrackUseCase }

    private var currentTrack: Music? = null
    private var userSeeking = false
    private var favoriteOverride: Boolean? = null

    override fun onCreateBinding(inflater: LayoutInflater): FragmentMainControl2Binding =
        FragmentMainControl2Binding.inflate(inflater)

    override fun onBindingCreated(binding: FragmentMainControl2Binding, savedInstanceState: Bundle?) {
        super.onBindingCreated(binding, savedInstanceState)

        setupInsets(binding)

        binding.mainControlPrevious.setImageResource(R.drawable.vector_main_play_previous)
        binding.mainControlNext.setImageResource(R.drawable.vector_main_play_next)
        binding.mainControlPlayPause.setImageResource(R.drawable.vector_main_play_pause_selector)
        binding.mainControlLeft.setImageResource(R.drawable.vector_favorite_selector)
        binding.mainControlRight.setImageResource(R.drawable.vector_play_queue_menu)

        binding.mainControlPlayPause.setOnClickListener(this)
        binding.mainControlPrevious.setOnClickListener(this)
        binding.mainControlNext.setOnClickListener(this)
        binding.mainControlBack.setOnClickListener(this)
        binding.mainControlAlbum.setOnClickListener(this)
        binding.mainControlTitle.setOnClickListener(this)
        binding.mainControlArtist.setOnClickListener(this)
        binding.mainControlLeft.setOnClickListener(this)
        binding.mainControlRight.setOnClickListener(this)
        binding.mainControlProgress.setOnSeekBarChangeListener(this)

        observePlayback()
    }


    private fun setupInsets(binding: FragmentMainControl2Binding) {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val navBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom

            view.updatePadding(
                bottom = navBottom
            )

            insets
        }

        ViewCompat.requestApplyInsets(binding.root)
    }

    private fun observePlayback() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.playbackState.collect { state ->
                    val binding = requireBinding()
                    val track = state.currentTrack
                    currentTrack = track

                    if (track == null) {
                        binding.mainControlTitle.text = getString(android.R.string.unknownName)
                        binding.mainControlArtist.text = getString(android.R.string.unknownName)
                        binding.mainControlCurrTime.text = viewModel.formatTime(0)
                        binding.mainControlTotalTime.text = viewModel.formatTime(0)
                        binding.mainControlProgress.setMax(100)
                        binding.mainControlProgress.setProgress(0)
                        binding.mainControlProgress.isEnabled = false
                        binding.mainControlPlayPause.isSelected = false
                        binding.mainControlLeft.isSelected = false
                        return@collect
                    }

                    binding.mainControlTitle.text = track.title
                    binding.mainControlArtist.text =
                        track.artist.ifBlank { getString(android.R.string.unknownName) }
                    binding.mainControlCurrTime.text = viewModel.formatTime(state.positionMs)
                    binding.mainControlTotalTime.text =
                        viewModel.formatTime(track.duration.coerceAtLeast(state.durationMs))
                    binding.mainControlProgress.setMax(track.duration.coerceAtLeast(1))
                    binding.mainControlProgress.isEnabled = !track.data.isNullOrBlank()
                    if (!userSeeking) {
                        binding.mainControlProgress.setProgress(state.positionMs.coerceAtLeast(0))
                    }
                    binding.mainControlPlayPause.isSelected = state.isPlaying
                    binding.mainControlLeft.isSelected = favoriteOverride ?: track.isFavorite()
                    track.loadMusicArtwork(binding.mainControlAlbum)
                }
            }
        }
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.main_control_play_pause -> viewModel.togglePlayPause(requireContext())
            R.id.main_control_previous -> viewModel.playPrevious(requireContext())
            R.id.main_control_next -> viewModel.playNext(requireContext())
            R.id.main_control_back -> (activity as? MainActivity)?.collapsePlayerPanel()
            R.id.main_control_right -> PlaybackQueueBottomSheetFragment.show(childFragmentManager)
            R.id.main_control_left -> toggleFavorite()
            R.id.main_control_album,
            R.id.main_control_title,
            R.id.main_control_artist -> ActivityPlayQueue.start(requireContext())
        }
    }

    private fun toggleFavorite() {
        val track = currentTrack ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            favoriteOverride = toggleFavoriteTrack(track._id)
            binding?.mainControlLeft?.isSelected = favoriteOverride == true
        }
    }

    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
        if (!fromUser) return
        binding?.mainControlCurrTime?.text = viewModel.formatTime(progress)
        viewModel.seekTo(requireContext(), progress)
    }

    override fun onStopTrackingTouch(seekBar: SeekBar) {
        userSeeking = false
    }

    override fun onStartTrackingTouch(seekBar: SeekBar) {
        userSeeking = true
    }
}
