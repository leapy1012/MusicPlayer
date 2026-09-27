package gd.app.musicplayer.feature.library.options

import android.content.Context
import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.parcelable
import gd.app.musicplayer.core.common.util.ToastUtil
import gd.app.musicplayer.core.designsystem.dialog.CouiConfirmDialogFragment
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.domain.model.MusicSet
import gd.app.musicplayer.domain.usecase.library.RemoveTrackFromGeneratedMusicSetUseCase
import gd.app.musicplayer.domain.usecase.playback.RemovePlaybackQueueItemUseCase
import gd.app.musicplayer.domain.usecase.playlist.RemoveTracksFromPlaylistUseCase
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class RemoveTrackFromSetConfirmDialogFragment : CouiConfirmDialogFragment() {

    @Inject
    @ApplicationContext
    lateinit var appContext: Context

    @Inject
    lateinit var removeTracksFromPlaylistUseCase: RemoveTracksFromPlaylistUseCase

    @Inject
    lateinit var removeTrackFromGeneratedMusicSetUseCase: RemoveTrackFromGeneratedMusicSetUseCase

    @Inject
    lateinit var removePlaybackQueueItemUseCase: RemovePlaybackQueueItemUseCase

    private val music: Music
        get() = requireNotNull(requireArguments().parcelable(ARG_MUSIC))
    private val musicSet: MusicSet
        get() = requireNotNull(requireArguments().parcelable(ARG_MUSIC_SET))

    override fun provideTitle(): CharSequence = getString(R.string.remove)
    override fun provideMessage(): CharSequence =
        getString(R.string.remove_song_from_list_msg, music.title)
    override fun providePositiveText(): CharSequence = getString(R.string.remove)

    override fun onPositiveClicked(extraChecked: Boolean) {
        setPositiveEnabled(false)
        lifecycleScope.launch {
            val removed = when (val set = musicSet) {
                is MusicSet.Playlist -> {
                    removeTracksFromPlaylistUseCase(set.id, listOf(music.id))
                    true
                }

                is MusicSet.Favorites -> {
                    removeTracksFromPlaylistUseCase(MusicSet.FAVORITES, listOf(music.id))
                    true
                }

                is MusicSet.Queue -> removePlaybackQueueItemUseCase(music)

                else -> removeTrackFromGeneratedMusicSetUseCase(set, music.id)
            }

            if (removed) {
                ToastUtil.show(requireContext(), R.string.succeed)
            }
            dismiss()
        }
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
