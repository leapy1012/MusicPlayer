package gd.app.musicplayer.feature.lyrics

import android.app.Activity
import android.app.Dialog
import android.os.Bundle
import android.text.Selection
import android.view.LayoutInflater
import android.view.WindowManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import com.coui.appcompat.dialog.COUIAlertDialogBuilder
import gd.app.musicplayer.R
import gd.app.musicplayer.core.designsystem.dialog.CouiAlertDialogSurface
import gd.app.musicplayer.databinding.DialogMusicPlaySearchLrcBinding
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.util.LyricsLoader
import gd.app.musicplayer.util.TrackLyricsStore
import kotlinx.coroutines.launch

class LyricSearchDialogFragment : DialogFragment() {

    private var binding: DialogMusicPlaySearchLrcBinding? = null

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

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val content = DialogMusicPlaySearchLrcBinding.inflate(LayoutInflater.from(requireContext()))
        binding = content

        content.edittext.setText(titleText)
        content.edittext.setFastDeletable(true)
        Selection.selectAll(content.edittext.text)
        content.dialogMessage.setText(R.string.search_lyric_message_local)

        content.lrcSearchReset.isVisible =
            !TrackLyricsStore.from(requireContext()).getTrackLyricPath(trackId).isNullOrBlank()
        content.lrcSearchReset.setOnClickListener {
            TrackLyricsStore.from(requireContext()).clearTrackLyricData(trackId)
            setFragmentResult(RESULT_KEY, Bundle.EMPTY)
            dismissAllowingStateLoss()
        }
        content.dialogButtonSearchEdit.setOnClickListener {
            lyricEditLauncher.launch(
                LyricEditActivity.intent(
                    context = requireContext(),
                    trackId = trackId,
                    title = titleText,
                    audioPath = audioPath
                )
            )
        }

        val builder = COUIAlertDialogBuilder(
            requireContext(),
            com.coui.appcompat.R.style.COUIAlertDialog_BottomAssignment
        )
            .setTitle(R.string.related_lyrics)
            .setView(content.root)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.lrc_manual_search, null)

        val dialog = builder.create()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                lyricListLauncher.launch(
                    LyricListActivity.intent(
                        context = requireContext(),
                        track = Music(
                            id = trackId,
                            title = content.edittext.text?.toString().orEmpty().ifBlank { titleText },
                            artist = artistText,
                            album = "",
                            albumId = "",
                            playlistId = -1,
                            data = audioPath
                        )
                    )
                )
            }
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setOnClickListener {
                dismiss()
            }
            builder.updateViewAfterShown()
            CouiAlertDialogSurface.apply(dialog)
        }

        lifecycleScope.launch {
            val result = LyricsLoader.load(requireContext(), trackId, audioPath)
            if (!isAdded) return@launch
            content.dialogButtonSearchEdit.text = getString(
                if (result.hasLyrics) R.string.equalizer_edit else R.string.add
            )
            content.dialogButtonSearchEdit.isVisible = true
        }

        return dialog
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
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
