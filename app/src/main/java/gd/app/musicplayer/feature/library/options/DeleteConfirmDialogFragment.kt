package gd.app.musicplayer.feature.library.options

import android.app.Activity
import android.content.ContentUris
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.parcelable
import gd.app.musicplayer.core.common.util.ToastUtil
import gd.app.musicplayer.core.designsystem.dialog.BaseDialogFragment
import gd.app.musicplayer.databinding.DialogCommonBinding
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.domain.model.MusicSet
import gd.app.musicplayer.domain.usecase.library.ObserveTracksUseCase
import gd.app.musicplayer.domain.usecase.playlist.DeletePlaylistUseCase
import gd.app.musicplayer.domain.usecase.track.DeleteTracksFromLibraryUseCase
import gd.app.musicplayer.domain.usecase.track.DeleteTracksUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class DeleteConfirmDialogFragment : BaseDialogFragment(), View.OnClickListener {

    private var _binding: DialogCommonBinding? = null
    private val binding: DialogCommonBinding
        get() = requireNotNull(_binding)

    @Inject
    lateinit var deleteTracksUseCase: DeleteTracksUseCase

    @Inject
    lateinit var deleteTracksFromLibraryUseCase: DeleteTracksFromLibraryUseCase

    @Inject
    lateinit var deletePlaylistUseCase: DeletePlaylistUseCase

    @Inject
    lateinit var observeTracksUseCase: ObserveTracksUseCase

    private var pendingSourceDeleteTracks: List<Music> = emptyList()
    private var waitingForSystemDeleteResult = false

    private val mediaDeleteLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val tracks = pendingSourceDeleteTracks
        pendingSourceDeleteTracks = emptyList()
        waitingForSystemDeleteResult = false

        viewLifecycleOwner.lifecycleScope.launch {
            if (result.resultCode == Activity.RESULT_OK && tracks.isNotEmpty()) {
                deleteTracksFromLibraryUseCase(tracks.map(Music::id))
                ToastUtil.show(requireContext(), R.string.succeed)
            } else {
                ToastUtil.show(requireContext(), R.string.feature_not_implemented)
            }
            dismiss()
        }
    }

    private val dialogType: Int
        get() = requireArguments().getInt(ARG_DIALOG_TYPE)
    private val itemName: String
        get() = requireArguments().getString(ARG_ITEM_NAME).orEmpty()
    private val itemCount: Int
        get() = requireArguments().getInt(ARG_ITEM_COUNT, 0)
    private val executionMode: Int
        get() = requireArguments().getInt(ARG_EXECUTION_MODE, EXECUTION_RESULT_ONLY)

    private val spec: Spec
        get() = when (dialogType) {
            TYPE_TRACK_DELETE -> Spec(
                titleRes = R.string.delete,
                message = getString(R.string.delete_file_tip, itemName),
                confirmTextRes = R.string.delete,
                showExtra = true,
                extraCheckedDefault = true
            )

            TYPE_SET_DELETE_PLAYLIST -> Spec(
                titleRes = R.string.delete,
                message = getString(R.string.delete_playlist_x, itemName),
                confirmTextRes = R.string.delete,
                showExtra = false,
                extraCheckedDefault = true
            )

            TYPE_TRACKS_DELETE -> Spec(
                titleRes = R.string.delete,
                message = getString(R.string.delete_x_songs, itemCount),
                confirmTextRes = R.string.delete,
                showExtra = true,
                extraCheckedDefault = true
            )

            TYPE_TRACK_REMOVE_FROM_LIST -> Spec(
                titleRes = R.string.remove,
                message = getString(R.string.remove_song_from_list_msg, itemName),
                confirmTextRes = R.string.remove,
                showExtra = false,
                extraCheckedDefault = false
            )

            else -> Spec(
                titleRes = R.string.delete,
                message = getString(R.string.dlg_delete_album_tip, itemName),
                confirmTextRes = R.string.delete,
                showExtra = true,
                extraCheckedDefault = true
            )
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogCommonBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        applyDialogBackground(binding.root)

        binding.dialogTitle.setText(spec.titleRes)
        binding.dialogMessage.text = spec.message
        binding.dialogButtonOk.setText(spec.confirmTextRes)
        binding.dialogButtonCancel.setOnClickListener(this)
        binding.dialogButtonOk.setOnClickListener(this)

        binding.dialogCommenExtraLayout.visibility = if (spec.showExtra) View.VISIBLE else View.GONE
        binding.dialogCommenDeleteSelect.isSelected = spec.extraCheckedDefault
        binding.dialogCommenExtraLayout.setOnClickListener(this)
        binding.dialogCommenDeleteSelect.setOnClickListener(this)
    }

    override fun onStart() {
        super.onStart()
        applyDialogWidth(0.9f)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.dialog_commen_extra_layout,
            R.id.dialog_commen_delete_select -> {
                binding.dialogCommenDeleteSelect.isSelected = !binding.dialogCommenDeleteSelect.isSelected
            }

            R.id.dialog_button_ok -> {
                val deleteSourceFile = binding.dialogCommenDeleteSelect.isSelected
                if (executionMode == EXECUTION_INTERNAL) {
                    executeConfirmedDelete(deleteSourceFile)
                } else {
                    setFragmentResult(
                        requireArguments().getString(ARG_RESULT_KEY).orEmpty(),
                        bundleOf(RESULT_CONFIRMED to true, RESULT_EXTRA_CHECKED to deleteSourceFile)
                    )
                    dismiss()
                }
            }

            R.id.dialog_button_cancel -> dismiss()
        }
    }

    private fun executeConfirmedDelete(deleteSourceFile: Boolean) {
        binding.dialogButtonOk.isEnabled = false
        binding.dialogButtonCancel.isEnabled = false

        viewLifecycleOwner.lifecycleScope.launch {
            val success = when (dialogType) {
                TYPE_TRACK_DELETE -> deleteTrack(deleteSourceFile)
                TYPE_SET_DELETE_PLAYLIST,
                TYPE_SET_DELETE_TRACKS -> deleteSet(deleteSourceFile)
                else -> false
            }

            if (waitingForSystemDeleteResult) {
                return@launch
            }

            ToastUtil.show(
                requireContext(),
                if (success) R.string.succeed else R.string.feature_not_implemented
            )
            dismiss()
        }
    }

    private suspend fun deleteTrack(deleteSourceFile: Boolean): Boolean {
        val music = requireArguments().parcelable<Music>(ARG_MUSIC) ?: return false
        val deletedCount = if (deleteSourceFile) {
            deleteTracksUseCase(listOf(music))
        } else {
            deleteTracksFromLibraryUseCase(listOf(music.id))
            1
        }
        if (deleteSourceFile && deletedCount == 0 && requestSystemMediaDelete(listOf(music))) {
            return true
        }
        return deletedCount > 0
    }

    private suspend fun deleteSet(deleteSourceFile: Boolean): Boolean {
        val musicSet = requireArguments().parcelable<MusicSet>(ARG_MUSIC_SET) ?: return false
        if (musicSet is MusicSet.Playlist) {
            deletePlaylistUseCase(musicSet.id)
            return true
        }

        val tracks = observeTracksUseCase(musicSet).first()
        if (tracks.isEmpty()) {
            ToastUtil.show(requireContext(), R.string.list_is_empty)
            return false
        }

        val deletedCount = if (deleteSourceFile) {
            deleteTracksUseCase(tracks)
        } else {
            deleteTracksFromLibraryUseCase(tracks.map { it.id })
            tracks.size
        }
        if (deleteSourceFile && deletedCount < tracks.size && requestSystemMediaDelete(tracks)) {
            return true
        }
        return deletedCount > 0
    }

    private fun requestSystemMediaDelete(tracks: List<Music>): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || tracks.isEmpty()) {
            return false
        }

        val uris = tracks
            .distinctBy(Music::id)
            .filter { it.id > 0L }
            .map { track ->
                ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    track.id
                )
            }

        if (uris.isEmpty()) return false

        val pendingIntent = runCatching {
            MediaStore.createDeleteRequest(requireContext().contentResolver, uris)
        }.getOrNull() ?: return false

        pendingSourceDeleteTracks = tracks
        waitingForSystemDeleteResult = true
        mediaDeleteLauncher.launch(
            IntentSenderRequest.Builder(pendingIntent.intentSender).build()
        )
        return true
    }

    companion object {
        const val RESULT_CONFIRMED = "confirmed"
        const val RESULT_EXTRA_CHECKED = "extra_checked"

        private const val ARG_ITEM_NAME = "item_name"
        private const val ARG_ITEM_COUNT = "item_count"
        private const val ARG_DIALOG_TYPE = "dialog_type"
        private const val ARG_RESULT_KEY = "result_key"
        private const val ARG_EXECUTION_MODE = "execution_mode"
        private const val ARG_MUSIC = "music"
        private const val ARG_MUSIC_SET = "music_set"

        private const val EXECUTION_RESULT_ONLY = 0
        private const val EXECUTION_INTERNAL = 1

        private const val TYPE_TRACK_DELETE = 1
        private const val TYPE_SET_DELETE_TRACKS = 2
        private const val TYPE_SET_DELETE_PLAYLIST = 3
        private const val TYPE_TRACK_REMOVE_FROM_LIST = 4
        private const val TYPE_TRACKS_DELETE = 5

        private data class Spec(
            val titleRes: Int,
            val message: String,
            val confirmTextRes: Int,
            val showExtra: Boolean,
            val extraCheckedDefault: Boolean
        )

        private fun create(
            resultKey: String,
            itemName: String,
            type: Int,
            itemCount: Int = 0,
            executionMode: Int = EXECUTION_RESULT_ONLY,
            music: Music? = null,
            musicSet: MusicSet? = null
        ): DeleteConfirmDialogFragment {
            return DeleteConfirmDialogFragment().apply {
                arguments = bundleOf(
                    ARG_RESULT_KEY to resultKey,
                    ARG_ITEM_NAME to itemName,
                    ARG_ITEM_COUNT to itemCount,
                    ARG_DIALOG_TYPE to type,
                    ARG_EXECUTION_MODE to executionMode
                ).apply {
                    music?.let { putParcelable(ARG_MUSIC, it) }
                    musicSet?.let { putParcelable(ARG_MUSIC_SET, it) }
                }
            }
        }

        fun forTrackDelete(
            resultKey: String,
            trackTitle: String,
            music: Music? = null
        ): DeleteConfirmDialogFragment {
            return create(
                resultKey = resultKey,
                itemName = trackTitle,
                type = TYPE_TRACK_DELETE,
                executionMode = if (music == null) EXECUTION_RESULT_ONLY else EXECUTION_INTERNAL,
                music = music
            )
        }

        fun forTrackRemoveFromList(resultKey: String, trackTitle: String): DeleteConfirmDialogFragment {
            return create(resultKey = resultKey, itemName = trackTitle, type = TYPE_TRACK_REMOVE_FROM_LIST)
        }

        fun forTracksDelete(resultKey: String, trackCount: Int): DeleteConfirmDialogFragment {
            return create(
                resultKey = resultKey,
                itemName = "",
                type = TYPE_TRACKS_DELETE,
                itemCount = trackCount
            )
        }

        fun forSetDelete(
            resultKey: String,
            setName: String,
            isPlaylist: Boolean,
            musicSet: MusicSet? = null
        ): DeleteConfirmDialogFragment {
            return create(
                resultKey = resultKey,
                itemName = setName,
                type = if (isPlaylist) TYPE_SET_DELETE_PLAYLIST else TYPE_SET_DELETE_TRACKS,
                executionMode = if (musicSet == null) EXECUTION_RESULT_ONLY else EXECUTION_INTERNAL,
                musicSet = musicSet
            )
        }
    }
}
