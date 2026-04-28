package gd.app.musicplayer.ui.feature.playlist

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.DrawableCompat
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import gd.app.musicplayer.R
import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.data.model.MusicSet
import gd.app.musicplayer.databinding.DialogNewPlaylistBinding
import gd.app.musicplayer.ui.common.base.BaseThemedDialogFragment
import gd.app.musicplayer.core.util.ToastUtil
import gd.app.musicplayer.core.ui.drawable.ViewStateDrawables
import gd.app.musicplayer.core.extension.applyLengthFilter
import gd.app.musicplayer.core.extension.appDependencies
import gd.app.musicplayer.core.extension.parcelableArrayList
import gd.app.musicplayer.core.extension.parcelable
import gd.app.musicplayer.core.extension.hideKeyboard
import gd.app.musicplayer.core.extension.showKeyboardDelayed
import kotlinx.coroutines.launch

class PlaylistInputDialog : BaseThemedDialogFragment() {

    private lateinit var binding: DialogNewPlaylistBinding
    private var actionMode: Int = MODE_CREATE_AND_RETURN
    private var pendingTracks: List<Music> = emptyList()
    private var targetSet: MusicSet? = null
    private val playlistRepo by lazy { requireContext().appDependencies.playlistRepo }
    private val createPlaylist by lazy { requireContext().appDependencies.createPlaylistUseCase }
    private val renamePlaylist by lazy { requireContext().appDependencies.renamePlaylistUseCase }
    private val addTracksToPlaylists by lazy { requireContext().appDependencies.addTracksToPlaylistsUseCase }

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

        binding.dialogButtonOk.setOnClickListener { submit() }
        binding.dialogButtonCancel.setOnClickListener { dismiss() }

        binding.newPlaylistEdittext.apply {
            applyLengthFilter(120)
            showKeyboardDelayed()
            if (actionMode == MODE_RENAME_SET) {
                setText((targetSet as? MusicSet.Playlist)?.name.orEmpty())
            } else {
                lifecycleScope.launch {
                    setText(suggestNewPlaylistName())
                }
            }
        }

        applyDialogWidth(0.88f)
        applyDialogBackground(view)
        applyTagStyles(view)
    }

    override fun onDismiss(dialog: DialogInterface) {
        binding.newPlaylistEdittext.hideKeyboard()
        super.onDismiss(dialog)
    }

    override fun applyTaggedStyle(
        tag: String?,
        view: View,
        palette: DialogTagPalette
    ): Boolean {
        if (tag == "dialogEditText" && view is EditText) {
            super.applyTaggedStyle(tag, view, palette)
            view.setTextColor(palette.titleColor)
            view.setHintTextColor(ColorUtils.setAlphaComponent(palette.titleColor, 128))
            view.highlightColor = ColorUtils.setAlphaComponent(palette.accentColor, 77)

            view.background?.mutate()?.let { background ->
                val wrapped = DrawableCompat.wrap(background)
                DrawableCompat.setTintList(
                    wrapped,
                    ViewStateDrawables.focusedDefaultColors(
                        ColorUtils.setAlphaComponent(
                            palette.titleColor,
                            77
                        ), palette.accentColor
                    )
                )
                view.background = wrapped
            }
            return true
        }
        return super.applyTaggedStyle(tag, view, palette)
    }

    private fun submit() {
        val input = binding.newPlaylistEdittext.text.toString().trim()
        if (input.isEmpty()) {
            ToastUtil.show(requireContext(), R.string.equalizer_edit_input_error)
            return
        }

        lifecycleScope.launch {
            val currentPlaylistId = (targetSet as? MusicSet.Playlist)?.id ?: -1L
            if (playlistRepo.playlistNameExists(input, currentPlaylistId)) {
                ToastUtil.show(requireContext(), R.string.name_exist)
                return@launch
            }

            when (actionMode) {
                MODE_RENAME_SET -> renameSet(input)
                MODE_ADD_TRACKS_TO_SET -> addTracksToPlaylist(input)
                MODE_CREATE_AND_RETURN -> createPlaylistAndReturn(input)
            }

            dismissAllowingStateLoss()
        }
    }

    private suspend fun renameSet(newName: String) {
        val playlist = targetSet as? MusicSet.Playlist ?: run {
            ToastUtil.show(requireContext(), R.string.feature_not_implemented)
            return
        }
        renamePlaylist(playlist.id, newName)
        ToastUtil.show(requireContext(), R.string.rename_success)
    }

    private suspend fun addTracksToPlaylist(playlistName: String) {
        val playlistId = createPlaylist(playlistName)
        val added = addTracksToPlaylists(setOf(playlistId), pendingTracks)
        ToastUtil.show(
            requireContext(),
            if (added > 0) R.string.succeed else R.string.list_contains_music
        )
    }

    private suspend fun createPlaylistAndReturn(playlistName: String) {
        val playlistId = createPlaylist(playlistName)

        setFragmentResult(
            RESULT_REQUEST_KEY,
            Bundle().apply {
                putLong(RESULT_PLAYLIST_ID, playlistId)
                putString(RESULT_PLAYLIST_NAME, playlistName)
            }
        )
    }

    private suspend fun suggestNewPlaylistName(): String {
        val names = playlistRepo.getAllPlaylistNames().toSet()
        val base = "${getString(R.string.new_list)} "
        var index = 1
        while (names.contains("$base$index")) index++
        return "$base$index"
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
