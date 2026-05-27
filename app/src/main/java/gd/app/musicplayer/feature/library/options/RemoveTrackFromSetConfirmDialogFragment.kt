package gd.app.musicplayer.feature.library.options

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.parcelable
import gd.app.musicplayer.core.common.util.ToastUtil
import gd.app.musicplayer.core.designsystem.dialog.BaseDialogFragment
import gd.app.musicplayer.databinding.DialogCommonBinding
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.domain.model.MusicSet
import gd.app.musicplayer.domain.usecase.library.RemoveTrackFromGeneratedMusicSetUseCase
import gd.app.musicplayer.domain.usecase.playback.RemovePlaybackQueueItemUseCase
import gd.app.musicplayer.domain.usecase.playlist.RemoveTracksFromPlaylistUseCase
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class RemoveTrackFromSetConfirmDialogFragment : BaseDialogFragment(), View.OnClickListener {

    @Inject
    @ApplicationContext
    lateinit var appContext: Context

    @Inject
    lateinit var removeTracksFromPlaylistUseCase: RemoveTracksFromPlaylistUseCase

    @Inject
    lateinit var removeTrackFromGeneratedMusicSetUseCase: RemoveTrackFromGeneratedMusicSetUseCase

    @Inject
    lateinit var removePlaybackQueueItemUseCase: RemovePlaybackQueueItemUseCase

    private var _binding: DialogCommonBinding? = null
    private val binding: DialogCommonBinding
        get() = requireNotNull(_binding)

    private val music: Music
        get() = requireNotNull(requireArguments().parcelable(ARG_MUSIC))
    private val musicSet: MusicSet
        get() = requireNotNull(requireArguments().parcelable(ARG_MUSIC_SET))

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

        binding.dialogTitle.setText(R.string.remove)
        binding.dialogMessage.text = getString(R.string.remove_song_from_list_msg, music.title)
        binding.dialogButtonOk.setText(R.string.remove)
        binding.dialogButtonCancel.setOnClickListener(this)
        binding.dialogButtonOk.setOnClickListener(this)
        binding.dialogCommenExtraLayout.visibility = View.GONE
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
            R.id.dialog_button_cancel -> dismiss()
            R.id.dialog_button_ok -> removeTrack()
        }
    }

    private fun removeTrack() {
        viewLifecycleOwner.lifecycleScope.launch {
            val removed = when (val set = musicSet) {
                is MusicSet.Playlist -> {
                    removeTracksFromPlaylistUseCase(set.id, listOf(music.id))
                    true
                }

                is MusicSet.Favorites -> {
                    removeTracksFromPlaylistUseCase(MusicSet.FAVORITES, listOf(music.id))
                    true
                }

                is MusicSet.Queue -> removeFromQueue()

                else -> removeTrackFromGeneratedMusicSetUseCase(set, music.id)
            }

            if (removed) {
                ToastUtil.show(requireContext(), R.string.succeed)
            }
            dismiss()
        }
    }

    private suspend fun removeFromQueue(): Boolean {
        return removePlaybackQueueItemUseCase(music)
    }

    companion object {
        private const val ARG_MUSIC = "music"
        private const val ARG_MUSIC_SET = "music_set"

        fun newInstance(music: Music, musicSet: MusicSet): RemoveTrackFromSetConfirmDialogFragment {
            return RemoveTrackFromSetConfirmDialogFragment().apply {
                arguments = bundleOf(
                    ARG_MUSIC to music,
                    ARG_MUSIC_SET to musicSet
                )
            }
        }
    }
}
