package gd.app.musicplayer.feature.lyrics

import android.app.Activity
import android.os.Bundle
import android.text.Selection
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import gd.app.musicplayer.R
import gd.app.musicplayer.databinding.DialogMusicPlaySearchLrcBinding
import gd.app.musicplayer.core.designsystem.dialog.BaseDialogFragment
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.util.LyricsLoader
import gd.app.musicplayer.util.TrackLyricsStore
import kotlinx.coroutines.launch

class LyricSearchDialogFragment : BaseDialogFragment(), View.OnClickListener {

    private var _binding: DialogMusicPlaySearchLrcBinding? = null
    private val binding get() = checkNotNull(_binding)

    private val trackId: Long
        get() = requireArguments().getLong(ARG_TRACK_ID)

    private val titleText: String
        get() = requireArguments().getString(ARG_TITLE).orEmpty()

    private val artistText: String
        get() = requireArguments().getString(ARG_ARTIST).orEmpty()

    private val audioPath: String?
        get() = requireArguments().getString(ARG_AUDIO_PATH)

    private val lyricEditLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
            setFragmentResult(RESULT_KEY, Bundle.EMPTY)
            dismissAllowingStateLoss()
        }

    private val lyricListLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
            setFragmentResult(RESULT_KEY, Bundle.EMPTY)
            dismissAllowingStateLoss()
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogMusicPlaySearchLrcBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        applyDialogWidth(0.9f)
        applyDialogBackground(view)

        binding.edittext.setText(titleText)
        Selection.selectAll(binding.edittext.text)
        binding.dialogMessage.setText(R.string.search_lyric_message_local)

        binding.dialogButtonSearchOnline.isVisible = false
        binding.dialogButtonSearchEdit.setOnClickListener(this)
        binding.dialogButtonOk.setOnClickListener(this)
        binding.lrcSearchReset.setOnClickListener(this)

        binding.lrcSearchReset.isVisible =
            !TrackLyricsStore.from(requireContext()).getTrackLyricPath(trackId).isNullOrBlank()

        viewLifecycleOwner.lifecycleScope.launch {
            val result = LyricsLoader.load(requireContext(), trackId, audioPath)
            if (!isAdded) return@launch
            binding.dialogButtonSearchEdit.text = getString(
                if (result.hasLyrics) {
                    R.string.equalizer_edit
                } else {
                    R.string.add
                }
            )
            binding.dialogButtonSearchEdit.isVisible = true
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.dialog_button_ok -> {
                lyricListLauncher.launch(
                    LyricListActivity.intent(
                        context = requireContext(),
                        track = Music(
                            id = trackId,
                            title = titleText,
                            artist = artistText,
                            album = "",
                            albumId = "",
                            playlistId = -1,
                            data = audioPath
                        )
                    )
                )
            }

            R.id.lrc_search_reset -> {
                TrackLyricsStore.from(requireContext()).clearTrackLyricData(trackId)
                setFragmentResult(RESULT_KEY, Bundle.EMPTY)
                dismissAllowingStateLoss()
            }

            R.id.dialog_button_search_edit -> {
                lyricEditLauncher.launch(
                    LyricEditActivity.intent(
                        context = requireContext(),
                        trackId = trackId,
                        title = titleText,
                        audioPath = audioPath
                    )
                )
            }
        }
    }

    companion object {
        const val TAG = "LyricSearchDialog"
        const val RESULT_KEY = "lyric_search_result"
        private const val ARG_TRACK_ID = "track_id"
        private const val ARG_TITLE = "title"
        private const val ARG_ARTIST = "artist"
        private const val ARG_AUDIO_PATH = "audio_path"

        fun newInstance(
            trackId: Long,
            title: String,
            artist: String,
            audioPath: String?
        ): LyricSearchDialogFragment {
            return LyricSearchDialogFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_TRACK_ID, trackId)
                    putString(ARG_TITLE, title)
                    putString(ARG_ARTIST, artist)
                    putString(ARG_AUDIO_PATH, audioPath)
                }
            }
        }
    }
}
