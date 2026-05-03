package gd.app.musicplayer.ui.feature.lyrics

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import gd.app.musicplayer.R
import gd.app.musicplayer.databinding.FragmentPlayFullLyricBinding
import gd.app.musicplayer.playback.PlaybackGateway
import gd.app.musicplayer.playback.queue.currentTrack
import gd.app.musicplayer.ui.feature.player.MusicPlayActivity
import gd.app.musicplayer.util.LyricsLoader
import gd.app.musicplayer.util.PreferenceUtil
import gd.app.musicplayer.util.TrackLyricsStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class FullLyricFragment : Fragment() {

    private var _binding: FragmentPlayFullLyricBinding? = null
    private val binding get() = checkNotNull(_binding)
    private var lyricJob: Job? = null
    private var currentSource: String? = null
    private var isResumedForAutoScroll = false
    private var isPlaybackActive = false
    private var isAutoScrollPreferenceEnabled = false
    private var isAutoScrollAllowedByScreen = true

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlayFullLyricBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.fullLyricBack.setOnClickListener {
            (activity as? MusicPlayActivity)?.showPlayer()
        }
        binding.fullLyricView.setOnClickListener {
            (activity as? MusicPlayActivity)?.showPlayer()
        }
        applyLyricPreferences()
        isAutoScrollPreferenceEnabled = PreferenceUtil.getInstance(requireContext())
            .isLyricAutoScrollEnabled()
        observePlayback()
        observeLyricPreferenceChanges()
        updateLyricAutoScroll()
    }

    override fun onResume() {
        super.onResume()
        isResumedForAutoScroll = true
        applyLyricPreferences()
        updateLyricAutoScroll()
    }

    override fun onPause() {
        isResumedForAutoScroll = false
        updateLyricAutoScroll()
        super.onPause()
    }

    override fun onDestroyView() {
        lyricJob?.cancel()
        _binding = null
        super.onDestroyView()
    }

    private fun observePlayback() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                PlaybackGateway.state.collect { state ->
                    isPlaybackActive = state.isPlaying
                    val track = state.currentTrack
                    if (track == null) {
                        updateLyricAutoScroll()
                        return@collect
                    }
                    binding.fullLyricTitle.text = track.title
                    binding.fullLyricArtist.text =
                        track.artist.ifBlank { getString(R.string.artist) }
                    binding.fullLyricView.setCurrentTime(state.positionMs.toLong())
                    binding.fullLyricView.setTimeOffset(
                        TrackLyricsStore.from(requireContext()).getTrackLyricOffset(track.id)
                    )
                    updateLyricAutoScroll()
                    maybeLoadLyrics(track.id, track.data)
                }
            }
        }
    }

    private fun observeLyricPreferenceChanges() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                PreferenceUtil.getInstance(requireContext()).observePreferenceChanges(
                    "preference_lyric_color",
                    "preference_lyric_text_size",
                    "lyric_align",
                    "lyric_style",
                    "lyric_auto_scroll"
                ).collect {
                    isAutoScrollPreferenceEnabled = PreferenceUtil.getInstance(requireContext())
                        .isLyricAutoScrollEnabled()
                    applyLyricPreferences()
                    updateLyricAutoScroll()
                }
            }
        }
    }

    private fun maybeLoadLyrics(trackId: Long, source: String?) {
        if (source == currentSource) return
        currentSource = source
        lyricJob?.cancel()
        binding.fullLyricView.setLyricText(null)
        binding.fullLyricView.setTimeOffset(
            TrackLyricsStore.from(requireContext()).getTrackLyricOffset(trackId)
        )
        if (source.isNullOrBlank()) return

        lyricJob = viewLifecycleOwner.lifecycleScope.launch {
            val result = LyricsLoader.load(requireContext(), trackId, source)
            if (!isAdded || currentSource != source) return@launch
            binding.fullLyricView.setTimeOffset(
                TrackLyricsStore.from(requireContext()).getTrackLyricOffset(trackId)
            )
            binding.fullLyricView.setLyricText(result.text)
        }
    }

    private fun updateLyricAutoScroll() {
        if (!isAdded) return
        binding.fullLyricView.setAutoScroll(
            isAutoScrollAllowedByScreen &&
                isResumedForAutoScroll &&
                isAutoScrollPreferenceEnabled &&
                isPlaybackActive
        )
    }

    private fun applyLyricPreferences() {
        if (!isAdded) return
        val prefs = PreferenceUtil.getInstance(requireContext())
        binding.fullLyricView.setCurrentTextColor(prefs.getLyricColor())
        binding.fullLyricView.setTextSize(prefs.getLyricTextSize())
        binding.fullLyricView.setTextAlign(prefs.getLyricAlign())
        binding.fullLyricView.setTextTypeface(prefs.getLyricStyle())
    }

    companion object {
        const val TAG = "FullLyricFragment"
    }
}
