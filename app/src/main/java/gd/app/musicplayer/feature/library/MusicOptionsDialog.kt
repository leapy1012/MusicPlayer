package gd.app.musicplayer.feature.library

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.OvershootInterpolator
import android.view.animation.ScaleAnimation
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import gd.app.musicplayer.R
import gd.app.musicplayer.core.ui.extension.appContainer
import gd.app.musicplayer.core.ui.drawable.ViewStateDrawables
import gd.app.musicplayer.core.util.ToastUtil
import gd.app.musicplayer.data.model.ArtworkRequest
import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.data.model.MusicSet
import gd.app.musicplayer.data.model.isFavorite
import gd.app.musicplayer.feature.editor.ActivityAudioEditor
import gd.app.musicplayer.feature.playlist.ActivityPlaylistSelect
import gd.app.musicplayer.feature.selection.MusicShareSupport
import gd.app.musicplayer.feature.tags.EditTagsActivity
import gd.app.musicplayer.playback.MusicPlaybackController
import gd.app.musicplayer.ui.common.base.BaseBottomGridMenuDialog
import kotlinx.coroutines.launch

class MusicOptionsDialog : BaseBottomGridMenuDialog() {

    private lateinit var musicSet: MusicSet
    private lateinit var music: Music
    private var favoriteButton: ImageView? = null
    private val playNextTracks by lazy { requireContext().appContainer.playNextTracksUseCase }
    private val enqueueTracks by lazy { requireContext().appContainer.enqueueTracksUseCase }
    private val toggleFavoriteTrack by lazy { requireContext().appContainer.toggleFavoriteTrackUseCase }
    private val deleteTracks by lazy { requireContext().appContainer.deleteTracksUseCase }
    private val playlistRepo by lazy { requireContext().appContainer.playlistRepo }

    companion object {
        private const val ARG_MUSIC = "music"
        private const val ARG_SET = "set"

        fun newInstance(music: Music, musicSet: MusicSet): MusicOptionsDialog {
            return MusicOptionsDialog().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_MUSIC, music)
                    putParcelable(ARG_SET, musicSet)
                }
            }
        }
    }

    override fun onReadArguments(arguments: Bundle) {
        music = requireNotNull(arguments.getParcelable(ARG_MUSIC))
        musicSet = requireNotNull(arguments.getParcelable(ARG_SET))
    }

    override fun provideMenuItems(): List<MenuItem> {
        return buildList {
            add(MenuItem.create(R.string.play_next_2, R.drawable.ic_menu_next_play))
            add(MenuItem.create(R.string.add_to, R.drawable.ic_menu_add))
            add(MenuItem.create(R.string.operation_enqueue, R.drawable.ic_menu_enqueue))
            add(MenuItem.create(R.string.dlg_ringtone_2, R.drawable.ic_menu_ringtone))
            add(MenuItem.create(R.string.audio_editor_title, R.drawable.ic_audio_editor))
            add(MenuItem.create(R.string.dlg_manage_artwork, R.drawable.ic_menu_artwork))
            add(MenuItem.create(R.string.dlg_share_music, R.drawable.ic_menu_share))

            if (musicSet.id.toInt() == -11 || musicSet.id.toInt() == -2 || musicSet.id > 0) {
                add(MenuItem.create(R.string.remove, R.drawable.ic_menu_remove))
            } else {
                add(MenuItem.create(R.string.delete, R.drawable.ic_menu_delete))
            }
        }
    }

    override fun onMenuItemClicked(item: MenuItem) {
        dismiss()

        when (item.id) {
            R.string.operation_enqueue -> {
                enqueueTracks(requireContext(), listOf(music))
                ToastUtil.show(requireContext(), getString(R.string.enqueue_msg_count, 1))
            }

            R.string.dlg_manage_artwork -> {
                ManageArtworkDialogFragment.newInstance(
                    ArtworkRequest.Track(music)
                ).show(parentFragmentManager, ManageArtworkDialogFragment::class.java.simpleName)
            }

            R.string.add_to -> {
                ActivityPlaylistSelect.start(requireContext(), listOf(music))
            }

            R.string.dlg_ringtone_2 -> {
                ToastUtil.show(requireContext(), R.string.feature_not_implemented)
            }

            R.string.dlg_share_music -> {
                MusicShareSupport.share(requireContext(), listOf(music))
            }

            R.string.remove -> {
                removeFromCurrentSet()
            }

            R.string.delete -> {
                confirmDeleteTrack()
            }

            R.string.play_next_2 -> {
                playNextTracks(requireContext(), listOf(music))
                ToastUtil.show(requireContext(), getString(R.string.enqueue_msg_count, 1))
            }

            R.string.audio_editor_title -> {
                ActivityAudioEditor.Companion.start(requireContext(), music)
            }
        }
    }

    override fun onBindTitleArea(
        container: View,
        titleView: TextView,
        titleIconView: ImageView
    ) {
        val titleContainer = container as ViewGroup
        titleContainer.removeAllViews()

        LayoutInflater.from(container.context)
            .inflate(R.layout.dialog_music_detail_title, titleContainer, true)

        val songTitleView = container.findViewById<TextView>(R.id.bottom_menu_title)

        songTitleView.text = music.title
        songTitleView.setOnClickListener {
            EditTagsActivity.Companion.start(requireContext(), music)
            dismiss()
        }

        favoriteButton = container.findViewById<ImageView>(R.id.bottom_menu_title_icon).apply {
            setImageDrawable(
                ViewStateDrawables.selectedDefaultDrawableFromRes(
                    context,
                    intArrayOf(R.drawable.ic_menu_favorite, R.drawable.ic_menu_favorite_selected)
                )
            )
            isSelected = music.isFavorite()
            setOnClickListener {
                toggleFavorite()
            }
        }

        container.findViewById<ImageView>(R.id.bottom_menu_title_icon_2).apply {
            setImageResource(R.drawable.ic_menu_song_detail)
            setOnClickListener {
                dismiss()
                MusicDetailDialogFragment.newInstance(music)
                    .show(parentFragmentManager, MusicDetailDialogFragment::class.java.simpleName)
            }
        }
    }

    fun onMusicChanged(updatedMusic: Music) {
        if (music._id == updatedMusic._id) {
            music = updatedMusic
            favoriteButton?.isSelected = updatedMusic.isFavorite()
        }
    }

    private fun toggleFavorite() {
        val favoriteView = favoriteButton ?: return
        favoriteView.startAnimation(createFavoriteAnimation())
        viewLifecycleOwner.lifecycleScope.launch {
            val selected = toggleFavoriteTrack(music._id)
            music = music.copy(p_id = if (selected) MusicSet.FAVORITES_ID else 0L)
            favoriteView.isSelected = selected
        }
    }

    private fun removeFromCurrentSet() {
        viewLifecycleOwner.lifecycleScope.launch {
            when (val set = musicSet) {
                is MusicSet.Playlist -> {
                    playlistRepo.removeTracksFromPlaylist(set.id, listOf(music._id))
                    ToastUtil.show(requireContext(), R.string.succeed)
                }

                is MusicSet.Favorites -> {
                    if (music.isFavorite()) {
                        toggleFavoriteTrack(music._id)
                    }
                    music = music.copy(p_id = 0L)
                    favoriteButton?.isSelected = false
                    ToastUtil.show(requireContext(), R.string.succeed)
                }

                is MusicSet.Queue -> {
                    val state = MusicPlaybackController.state.value
                    val index = state.queue.indexOfFirst { it._id == music._id }
                    if (index >= 0) {
                        val newQueue = state.queue.toMutableList().apply { removeAt(index) }
                        val newIndex = when {
                            newQueue.isEmpty() -> -1
                            index < state.currentIndex -> state.currentIndex - 1
                            state.currentIndex >= newQueue.size -> newQueue.lastIndex
                            else -> state.currentIndex
                        }
                        MusicPlaybackController.replaceQueue(requireContext(), newQueue, newIndex)
                        ToastUtil.show(requireContext(), R.string.succeed)
                    }
                }

                else -> ToastUtil.show(requireContext(), R.string.feature_not_implemented)
            }
        }
    }

    private fun confirmDeleteTrack() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.delete)
            .setMessage(music.title)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    val deletedCount = deleteTracks(listOf(music))
                    ToastUtil.show(
                        requireContext(),
                        if (deletedCount > 0) R.string.succeed else R.string.feature_not_implemented
                    )
                }
            }
            .show()
    }

    private fun createFavoriteAnimation(): Animation {
        return ScaleAnimation(
            0.5f,
            1.0f,
            0.5f,
            1.0f,
            Animation.RELATIVE_TO_SELF,
            0.5f,
            Animation.RELATIVE_TO_SELF,
            0.5f
        ).apply {
            duration = 1000L
            interpolator = OvershootInterpolator(0.4f)
            repeatMode = Animation.REVERSE
        }
    }
}
