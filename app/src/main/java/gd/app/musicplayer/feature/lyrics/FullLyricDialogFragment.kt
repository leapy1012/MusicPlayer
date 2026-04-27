package gd.app.musicplayer.feature.lyrics

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import gd.app.musicplayer.R
import gd.app.musicplayer.databinding.FragmentPlayFullLyricBinding
import gd.app.musicplayer.playback.MusicPlaybackController
import gd.app.musicplayer.util.LyricsLoader
import gd.app.musicplayer.util.PreferenceUtil
import gd.app.musicplayer.util.TrackLyricsStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class FullLyricDialogFragment : DialogFragment() {

    private var _binding: FragmentPlayFullLyricBinding? = null
    private val binding get() = checkNotNull(_binding)
    private var lyricJob: Job? = null
    private var currentSource: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, R.style.AppTheme)
    }

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
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        dialog?.window?.setDimAmount(1f)
        binding.root.setBackgroundColor(Color.BLACK)
        binding.fullLyricBack.setOnClickListener { dismissAllowingStateLoss() }
        binding.fullLyricView.setOnClickListener { dismissAllowingStateLoss() }
        applyLyricPreferences()
        observePlayback()
    }

    override fun onResume() {
        super.onResume()
        applyLyricPreferences()
    }

    override fun onDestroyView() {
        lyricJob?.cancel()
        _binding = null
        super.onDestroyView()
    }

    private fun observePlayback() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                MusicPlaybackController.state.collect { state ->
                    val track = state.currentTrack ?: return@collect
                    binding.fullLyricTitle.text = track.title
                    binding.fullLyricArtist.text =
                        track.artist.ifBlank { getString(android.R.string.unknownName) }
                    binding.fullLyricView.setCurrentTime(state.positionMs.toLong())
                    maybeLoadLyrics(track._id, track.data)
                }
            }
        }
    }

    private fun maybeLoadLyrics(trackId: Long, source: String?) {
        if (source == currentSource) return
        currentSource = source
        lyricJob?.cancel()
        binding.fullLyricView.setLyricText(null)
        if (source.isNullOrBlank()) return

        lyricJob = viewLifecycleOwner.lifecycleScope.launch {
            val result = LyricsLoader.load(requireContext(), trackId, source)
            if (!isAdded || currentSource != source) return@launch
            binding.fullLyricView.setTimeOffset(TrackLyricsStore.from(requireContext()).getTrackLyricOffset(trackId))
            binding.fullLyricView.setLyricText(result.text)
        }
    }

    private fun applyLyricPreferences() {
        val preferenceUtil = PreferenceUtil.getInstance(requireContext())
        binding.fullLyricView.setCurrentTextColor(preferenceUtil.getLyricColor())
        binding.fullLyricView.setTextSize(preferenceUtil.getLyricTextSize().toFloat())
        binding.fullLyricView.setTextAlign(preferenceUtil.getLyricAlign())
        binding.fullLyricView.setTextTypeface(preferenceUtil.getLyricStyle())
        binding.fullLyricView.setAutoScroll(preferenceUtil.isLyricAutoScrollEnabled())
    }

    companion object {
        const val TAG = "FullLyricDialog"

        fun show(manager: FragmentManager) {
            if (manager.findFragmentByTag(TAG) != null) return
            FullLyricDialogFragment().show(manager, TAG)
        }
    }
}
