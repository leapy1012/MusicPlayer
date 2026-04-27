package gd.app.musicplayer.feature.lyrics

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.Selection
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import gd.app.musicplayer.R
import gd.app.musicplayer.core.util.ToastUtil
import gd.app.musicplayer.databinding.DialogMusicPlaySearchLrcBinding
import gd.app.musicplayer.ui.common.base.BaseThemedDialogFragment
import gd.app.musicplayer.util.LyricsLoader
import gd.app.musicplayer.util.TrackLyricsStore
import java.io.File
import kotlinx.coroutines.launch

class LyricSearchDialogFragment : BaseThemedDialogFragment(), View.OnClickListener {

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

    private val localPicker =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri == null) return@registerForActivityResult
            importLocalLyrics(uri)
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
                localPicker.launch("*/*")
            }

            R.id.lrc_search_reset -> {
                TrackLyricsStore.from(requireContext()).clearTrackLyricData(trackId)
                setFragmentResult(RESULT_KEY, Bundle.EMPTY)
                dismissAllowingStateLoss()
            }

            R.id.dialog_button_search_edit -> {
                dismissAllowingStateLoss()
                LyricManualEditDialogFragment.newInstance(
                    trackId = trackId,
                    title = titleText,
                    audioPath = audioPath
                ).show(parentFragmentManager, LyricManualEditDialogFragment.TAG)
            }
        }
    }

    private fun importLocalLyrics(uri: Uri) {
        val context = context ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val importedPath = runCatching {
                val extension = resolveExtension(uri)
                val target = File(context.filesDir, "lyrics/imported/track_${trackId}.$extension")
                target.parentFile?.mkdirs()
                context.contentResolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                } ?: error("Unable to open source")
                target.absolutePath
            }.getOrNull()

            if (importedPath == null) {
                ToastUtil.show(context, R.string.permission_open_failed)
                return@launch
            }

            TrackLyricsStore.from(context).setTrackLyricPath(trackId, importedPath)
            setFragmentResult(RESULT_KEY, Bundle.EMPTY)
            dismissAllowingStateLoss()
        }
    }

    private fun resolveExtension(uri: Uri): String {
        val name = runCatching {
            requireContext().contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0)
                } else {
                    null
                }
            }
        }.getOrNull()
        val candidate = name ?: uri.lastPathSegment.orEmpty()
        val ext = candidate.substringAfterLast('.', "")
        return when (ext.lowercase()) {
            "lrc", "txt" -> ext.lowercase()
            else -> "lrc"
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
