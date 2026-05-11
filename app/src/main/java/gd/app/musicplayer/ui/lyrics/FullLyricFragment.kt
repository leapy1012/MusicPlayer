package gd.app.musicplayer.ui.lyrics

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.spToPx
import gd.app.musicplayer.databinding.FragmentPlayFullLyricBinding
import gd.app.musicplayer.ui.player.full.MusicPlayActivity
import gd.app.musicplayer.ui.player.full.PlayerViewModel
import kotlinx.coroutines.launch

@AndroidEntryPoint
class FullLyricFragment : Fragment() {

    private var _binding: FragmentPlayFullLyricBinding? = null
    private val binding get() = checkNotNull(_binding)

    private val fullLyricViewModel: FullLyricViewModel by viewModels()
    private val playerViewModel: PlayerViewModel by viewModels()

    private var isResumedForAutoScroll: Boolean = false
    private var isAutoScrollAllowedByScreen: Boolean = true

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlayFullLyricBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        setupClicks()
        observePlayback()
        observeUiState()
    }

    override fun onResume() {
        super.onResume()
        isResumedForAutoScroll = true
        updateLyricAutoScroll(fullLyricViewModel.uiState.value)
    }

    override fun onPause() {
        isResumedForAutoScroll = false
        updateLyricAutoScroll(fullLyricViewModel.uiState.value)
        super.onPause()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun setupClicks() = with(binding) {
        fullLyricBack.setOnClickListener {
            showPlayer()
        }

        fullLyricView.setOnClickListener {
            showPlayer()
        }
    }

    private fun observePlayback() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                playerViewModel.trackUiState.collect { state ->

//                    fullLyricViewModel.onPlaybackChanged(
//                        title = state.title,
//                        artist = state.artist,
////                        positionMs = state.positionMs.toLong(),
////                        trackId = track?.id,
////                        source = track?.data,
////                        isPlaying = state.isPlaying
//                    )
                }
            }
        }
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                fullLyricViewModel.uiState.collect { state ->
                    render(state)
                }
            }
        }
    }

    private fun render(state: FullLyricUiState) = with(binding) {
        fullLyricTitle.text = state.title
        fullLyricArtist.text = state.artist.ifBlank {
            getString(R.string.artist)
        }

        fullLyricView.setCurrentTime(state.positionMs)
        fullLyricView.setTimeOffset(state.lyricOffset.toInt())
        fullLyricView.setLyricText(state.lyricText)

        fullLyricView.setCurrentTextColor(state.lyricPreferences.lyricColor)
        fullLyricView.setTextSize(requireContext().spToPx(state.lyricPreferences.lyricTextSize).toInt())
        fullLyricView.setTextAlign(state.lyricPreferences.lyricAlign)
        fullLyricView.setTextTypeface(state.lyricPreferences.lyricStyle)

        updateLyricAutoScroll(state)
    }

    private fun updateLyricAutoScroll(state: FullLyricUiState) {
        if (_binding == null) return

        binding.fullLyricView.setAutoScroll(
            isAutoScrollAllowedByScreen &&
                    isResumedForAutoScroll &&
                    state.lyricPreferences.lyricAutoScrollEnabled &&
                    state.isPlaying
        )
    }

    private fun showPlayer() {
        (activity as? MusicPlayActivity)?.showPlayer()
    }

    companion object {
        const val TAG = "FullLyricFragment"
    }
}