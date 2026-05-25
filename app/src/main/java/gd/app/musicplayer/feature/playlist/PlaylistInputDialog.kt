package gd.app.musicplayer.feature.playlist

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.domain.model.MusicSet
import gd.app.musicplayer.databinding.DialogNewPlaylistBinding
import gd.app.musicplayer.core.designsystem.dialog.BaseDialogFragment
import gd.app.musicplayer.core.common.util.ToastUtil
import gd.app.musicplayer.core.common.extension.applyLengthFilter
import gd.app.musicplayer.core.common.extension.parcelableArrayList
import gd.app.musicplayer.core.common.extension.parcelable
import gd.app.musicplayer.core.common.extension.hideKeyboard
import gd.app.musicplayer.core.common.extension.showKeyboardDelayed
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PlaylistInputDialog : BaseDialogFragment() {
    private val viewModel: PlaylistInputViewModel by viewModels()

    private lateinit var binding: DialogNewPlaylistBinding
    private var actionMode: Int = MODE_CREATE_AND_RETURN
    private var pendingTracks: List<Music> = emptyList()
    private var targetSet: MusicSet? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            actionMode = it.getInt(ARG_TARGET, MODE_CREATE_AND_RETURN)
            targetSet = it.parcelable(ARG_SET)
            pendingTracks = it.parcelableArrayList<Music>(ARG_PENDING_TRACKS)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        state: Bundle?
    ): View {

        binding = DialogNewPlaylistBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.dialogButtonOk.setOnClickListener {
            viewModel.submit(binding.newPlaylistEdittext.text.toString())
        }
        binding.dialogButtonCancel.setOnClickListener { dismiss() }

        binding.newPlaylistEdittext.apply {
            applyLengthFilter(120)
            showKeyboardDelayed()
        }

        observeViewModel()
        viewModel.initialize(
            mode = actionMode,
            targetSet = targetSet,
            pendingTracks = pendingTracks,
            newListLabel = getString(R.string.new_list)
        )
        binding.newPlaylistTitle.setText(
            if (actionMode == MODE_RENAME_SET) R.string.list_rename else R.string.create_playlist
        )

        applyDialogWidth(0.88f)
        applyDialogBackground(view)
    }

    override fun onDismiss(dialog: DialogInterface) {
        binding.newPlaylistEdittext.hideKeyboard()
        super.onDismiss(dialog)
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        val currentInput = binding.newPlaylistEdittext.text?.toString().orEmpty()
                        if (currentInput.isBlank() && state.suggestedName.isNotBlank()) {
                            binding.newPlaylistEdittext.setText(state.suggestedName)
                            binding.newPlaylistEdittext.setSelection(state.suggestedName.length)
                        }
                    }
                }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            is PlaylistInputEvent.ShowToast -> ToastUtil.show(requireContext(), event.messageRes)
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
