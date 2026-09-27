package gd.app.musicplayer.feature.playlist

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.coui.appcompat.dialog.COUIAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.applyLengthFilter
import gd.app.musicplayer.core.common.extension.hideKeyboard
import gd.app.musicplayer.core.common.extension.parcelable
import gd.app.musicplayer.core.common.extension.parcelableArrayList
import gd.app.musicplayer.core.common.extension.showKeyboardDelayed
import gd.app.musicplayer.core.common.util.ToastUtil
import gd.app.musicplayer.databinding.DialogNewPlaylistBinding
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.domain.model.MusicSet
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PlaylistInputDialog : DialogFragment() {
    private val viewModel: PlaylistInputViewModel by viewModels()

    private var actionMode: Int = MODE_CREATE_AND_RETURN
    private var pendingTracks: List<Music> = emptyList()
    private var targetSet: MusicSet? = null
    private var editBinding: DialogNewPlaylistBinding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            actionMode = it.getInt(ARG_TARGET, MODE_CREATE_AND_RETURN)
            targetSet = it.parcelable(ARG_SET)
            pendingTracks = it.parcelableArrayList(ARG_PENDING_TRACKS)
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = DialogNewPlaylistBinding.inflate(LayoutInflater.from(requireContext()))
        editBinding = binding

        binding.newPlaylistEdittext.apply {
            applyLengthFilter(120)
            setFastDeletable(true)
            showKeyboardDelayed()
        }

        val titleRes = if (actionMode == MODE_RENAME_SET) {
            R.string.list_rename
        } else {
            R.string.create_playlist
        }

        val builder = COUIAlertDialogBuilder(
            requireContext(),
            com.coui.appcompat.R.style.COUIAlertDialog_BottomAssignment
        )
            .setTitle(titleRes)
            .setView(binding.root)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.ok, null)

        val dialog = builder.create()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                viewModel.submit(binding.newPlaylistEdittext.text?.toString().orEmpty())
            }
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setOnClickListener {
                dismiss()
            }
            builder.updateViewAfterShown()
        }

        observeViewModel()
        viewModel.initialize(
            mode = actionMode,
            targetSet = targetSet,
            pendingTracks = pendingTracks,
            newListLabel = getString(R.string.new_list)
        )
        return dialog
    }

    override fun onDismiss(dialog: DialogInterface) {
        editBinding?.newPlaylistEdittext?.hideKeyboard()
        editBinding = null
        super.onDismiss(dialog)
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        val edit = editBinding?.newPlaylistEdittext ?: return@collect
                        val currentInput = edit.text?.toString().orEmpty()
                        if (currentInput.isBlank() && state.suggestedName.isNotBlank()) {
                            edit.setText(state.suggestedName)
                            edit.setSelection(state.suggestedName.length)
                        }
                    }
                }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            is PlaylistInputEvent.ShowToast ->
                                ToastUtil.show(requireContext(), event.messageRes)

                            is PlaylistInputEvent.ReturnCreatedPlaylist -> {
                                setFragmentResult(
                                    RESULT_REQUEST_KEY,
                                    Bundle().apply {
                                        putLong(RESULT_PLAYLIST_ID, event.playlistId)
                                        putString(RESULT_PLAYLIST_NAME, event.playlistName)
                                    }
                                )
                            }

                            is PlaylistInputEvent.ReturnRenamedSet -> {
                                setFragmentResult(
                                    RESULT_REQUEST_KEY,
                                    Bundle().apply {
                                        putParcelable(RESULT_RENAMED_SET, event.musicSet)
                                    }
                                )
                            }

                            PlaylistInputEvent.Dismiss -> dismissAllowingStateLoss()
                        }
                    }
                }
            }
        }
    }

    companion object {
        private const val ARG_TARGET = "target"
        private const val ARG_SET = "set"
        private const val ARG_PENDING_TRACKS = "pending_tracks"

        const val MODE_CREATE_AND_RETURN = 0
        const val MODE_RENAME_SET = 1
        const val MODE_ADD_TRACKS_TO_SET = 2

        const val RESULT_REQUEST_KEY = "playlist_input_result"
        const val RESULT_PLAYLIST_ID = "playlist_id"
        const val RESULT_PLAYLIST_NAME = "playlist_name"
        const val RESULT_RENAMED_SET = "renamed_set"

        fun forMode(mode: Int): PlaylistInputDialog {
            return PlaylistInputDialog().apply {
                arguments = Bundle().apply {
                    putInt(ARG_TARGET, mode)
                }
            }
        }

        fun forSet(set: MusicSet, mode: Int): PlaylistInputDialog {
            return PlaylistInputDialog().apply {
                arguments = Bundle().apply {
                    putInt(ARG_TARGET, mode)
                    putParcelable(ARG_SET, set)
                }
            }
        }

        fun forTracks(tracks: List<Music>, mode: Int): PlaylistInputDialog {
            return PlaylistInputDialog().apply {
                arguments = Bundle().apply {
                    putInt(ARG_TARGET, mode)
                    putParcelableArrayList(ARG_PENDING_TRACKS, ArrayList(tracks))
                }
            }
        }
    }
}
