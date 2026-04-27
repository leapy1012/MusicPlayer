package gd.app.musicplayer.feature.lyrics

import android.os.Bundle
import android.text.Selection
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import gd.app.musicplayer.R
import gd.app.musicplayer.core.util.ToastUtil
import gd.app.musicplayer.databinding.DialogLyricManualEditBinding
import gd.app.musicplayer.ui.common.base.BaseThemedDialogFragment
import gd.app.musicplayer.util.LyricsLoader
import gd.app.musicplayer.util.TrackLyricsStore
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LyricManualEditDialogFragment : BaseThemedDialogFragment(), View.OnClickListener {

    private var _binding: DialogLyricManualEditBinding? = null
    private val binding get() = checkNotNull(_binding)

    private val trackId: Long
        get() = requireArguments().getLong(ARG_TRACK_ID)

    private val title: String
        get() = requireArguments().getString(ARG_TITLE).orEmpty()

    private val audioPath: String?
        get() = requireArguments().getString(ARG_AUDIO_PATH)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogLyricManualEditBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        applyDialogWidth(0.94f)
        applyDialogBackground(view)

        binding.lyricManualTitle.text =
            getString(R.string.equalizer_edit) + if (title.isBlank()) "" else " - $title"
        binding.lyricManualCancel.setOnClickListener(this)
        binding.lyricManualSave.setOnClickListener(this)

        viewLifecycleOwner.lifecycleScope.launch {
            val result = LyricsLoader.load(requireContext(), trackId, audioPath)
            if (!isAdded || binding.lyricManualInput.text?.isNotEmpty() == true) return@launch
            binding.lyricManualInput.setText(result.text.orEmpty())
            binding.lyricManualDelete.isVisible = result.hasLyrics
            if (result.hasLyrics) {
                Selection.setSelection(binding.lyricManualInput.text!!, 0)
            }
        }

        binding.lyricManualDelete.setOnClickListener(this)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.lyric_manual_cancel -> dismissAllowingStateLoss()
            R.id.lyric_manual_delete -> {
                TrackLyricsStore.from(requireContext()).clearTrackLyricData(trackId)
                setFragmentResult(LyricSearchDialogFragment.RESULT_KEY, Bundle.EMPTY)
                dismissAllowingStateLoss()
            }

            R.id.lyric_manual_save -> saveLyrics()
        }
    }

    private fun saveLyrics() {
        val text = binding.lyricManualInput.text?.toString()?.trim().orEmpty()
        if (text.isBlank()) {
            ToastUtil.show(requireContext(), R.string.equalizer_edit_input_error)
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val path = withContext(Dispatchers.IO) {
                val file = File(requireContext().filesDir, "lyrics/edited/track_${trackId}.lrc")
                file.parentFile?.mkdirs()
                file.writeText(text)
                file.absolutePath
            }
            TrackLyricsStore.from(requireContext()).setTrackLyricPath(trackId, path)
            setFragmentResult(LyricSearchDialogFragment.RESULT_KEY, Bundle.EMPTY)
            dismissAllowingStateLoss()
        }
    }

    companion object {
        const val TAG = "LyricManualEditDialog"
        private const val ARG_TRACK_ID = "track_id"
        private const val ARG_TITLE = "title"
        private const val ARG_AUDIO_PATH = "audio_path"

        fun newInstance(
            trackId: Long,
            title: String,
            audioPath: String?
        ): LyricManualEditDialogFragment {
            return LyricManualEditDialogFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_TRACK_ID, trackId)
                    putString(ARG_TITLE, title)
                    putString(ARG_AUDIO_PATH, audioPath)
                }
            }
        }
    }
}
