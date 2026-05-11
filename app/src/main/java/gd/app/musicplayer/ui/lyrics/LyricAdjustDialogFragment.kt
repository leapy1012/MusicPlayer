package gd.app.musicplayer.ui.lyrics

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.setFragmentResult
import gd.app.musicplayer.R
import gd.app.musicplayer.databinding.DialogLyricAdjustBinding
import gd.app.musicplayer.core.designsystem.dialog.BaseDialogFragment
import gd.app.musicplayer.util.TrackLyricsStore

class LyricAdjustDialogFragment : BaseDialogFragment(), View.OnClickListener {

    private var _binding: DialogLyricAdjustBinding? = null
    private val binding get() = checkNotNull(_binding)

    private val trackId: Long
        get() = requireArguments().getLong(ARG_TRACK_ID)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogLyricAdjustBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        applyDialogWidth(0.9f)
        applyDialogBackground(view)
        binding.lrcTimeBack.setOnClickListener(this)
        binding.lrcTimeForward.setOnClickListener(this)
        binding.lrcTimeUndo.setOnClickListener(this)
        binding.lyricAdjustClose.setOnClickListener(this)
        renderOffset()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.lrc_time_back -> updateOffset(-100)
            R.id.lrc_time_forward -> updateOffset(100)
            R.id.lrc_time_undo -> setOffset(0)
            R.id.lyric_adjust_close -> dismissAllowingStateLoss()
        }
    }

    private fun updateOffset(deltaMs: Int) {
        val store = TrackLyricsStore.from(requireContext())
        setOffset(store.getTrackLyricOffset(trackId) + deltaMs)
    }

    private fun setOffset(offsetMs: Int) {
        TrackLyricsStore.from(requireContext()).setTrackLyricOffset(trackId, offsetMs)
        setFragmentResult(RESULT_KEY, Bundle.EMPTY)
        renderOffset()
    }

    private fun renderOffset() {
        val offsetMs = TrackLyricsStore.from(requireContext()).getTrackLyricOffset(trackId)
        binding.lyricAdjustTitle.text = if (offsetMs == 0) {
            getString(R.string.lrc_progress) + " : " + getString(R.string.lrc_time_normal)
        } else {
            val seconds = offsetMs / 1000f
            val prefix = if (seconds > 0f) "+" else ""
            getString(R.string.lrc_progress) + " : " + prefix + seconds + "s"
        }
    }

    companion object {
        const val TAG = "LyricAdjustDialog"
        const val RESULT_KEY = "lyric_adjust_result"
        private const val ARG_TRACK_ID = "track_id"

        fun newInstance(trackId: Long): LyricAdjustDialogFragment {
            return LyricAdjustDialogFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_TRACK_ID, trackId)
                }
            }
        }
    }
}
