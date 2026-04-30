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
import gd.app.musicplayer.playback.PlaybackControllerProvider
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
        observePlayback()
        observeLyricPreferenceChanges()
    }

    override fun onResume() {
        super.onResume()
        applyLyricPreferences()
        binding.fullLyricView.setAutoScroll(true)
    }

    override fun onPause() {
        binding.fullLyricView.setAutoScroll(false)
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
                PlaybackControllerProvider.state.collect { state ->
                    val track = state.currentTrack ?: return@collect
                    binding.fullLyricTitle.text = track.title
                    binding.fullLyricArtist.text =
                        track.artist.ifBlank { getString(R.string.artist) }
                    binding.fullLyricView.setCurrentTime(state.positionMs.toLong())
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
                    "lyric_style"
                ).collect {
                    applyLyricPreferences()
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

    private fun applyLyricPreferences() {
        if (!isAdded) return
        val prefs = PreferenceUtil.getInstance(requireContext())
        binding.fullLyricView.setCurrentTextColor(prefs.getLyricColor())
        binding.fullLyricView.setTextSize(prefs.getLyricTextSize().toFloat())
        binding.fullLyricView.setTextAlign(prefs.getLyricAlign())
        binding.fullLyricView.setTextTypeface(prefs.getLyricStyle())
    }

    companion object {
        const val TAG = "FullLyricFragment"
    }
}
