package gd.app.musicplayer.ui.feature.library

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.OvershootInterpolator
import android.view.animation.ScaleAnimation
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.core.extension.appDependencies
import gd.app.musicplayer.core.extension.parcelable
import gd.app.musicplayer.core.ui.drawable.ViewStateDrawables
import gd.app.musicplayer.core.util.ToastUtil
import gd.app.musicplayer.data.model.ArtworkRequest
import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.data.model.MusicSet
import gd.app.musicplayer.core.extension.isFavorite
import gd.app.musicplayer.ui.feature.editor.ActivityAudioEditor
import gd.app.musicplayer.ui.feature.playlist.ActivityPlaylistSelect
import gd.app.musicplayer.ui.feature.selection.MusicShareSupport
import gd.app.musicplayer.ui.feature.tags.EditTagsActivity
import gd.app.musicplayer.ui.common.base.BaseBottomGridMenuDialog
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MusicOptionsDialog : BaseBottomGridMenuDialog() {

    private lateinit var musicSet: MusicSet
    private lateinit var music: Music
    private val viewModel: MusicOptionsViewModel by viewModels()
    private var favoriteButton: ImageView? = null

    companion object {
        private const val ARG_MUSIC = "music"
        private const val ARG_SET = "set"
        private const val DELETE_CONFIRM_RESULT_KEY = "music_options_delete_confirm"

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
        music = requireNotNull(arguments.parcelable(ARG_MUSIC))
        musicSet = requireNotNull(arguments.parcelable(ARG_SET))
        viewModel.initialize(music, musicSet)
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
        when (item.id) {
            R.string.operation_enqueue -> {
                dismiss()
                viewModel.enqueue()
            }

            R.string.dlg_manage_artwork -> {
                dismiss()
                ManageArtworkDialogFragment.newInstance(
                    ArtworkRequest.Track(music)
                ).show(parentFragmentManager, ManageArtworkDialogFragment::class.java.simpleName)
            }

            R.string.add_to -> {
                dismiss()
                ActivityPlaylistSelect.start(requireContext(), listOf(music))
            }

            R.string.dlg_ringtone_2 -> {
                dismiss()
                ToastUtil.show(requireContext(), R.string.feature_not_implemented)
            }

            R.string.dlg_share_music -> {
                dismiss()
                MusicShareSupport.share(requireContext(), listOf(music))
            }

            R.string.remove -> {
                dismiss()
                viewModel.removeFromCurrentSet()
            }

            R.string.delete -> {
                confirmDeleteTrack()
            }

            R.string.play_next_2 -> {
                dismiss()
                viewModel.playNext()
            }

            R.string.audio_editor_title -> {
                dismiss()
                ActivityAudioEditor.start(requireContext(), music)
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
            EditTagsActivity.start(requireContext(), music)
            dismiss()
        }

        favoriteButton = container.findViewById<ImageView>(R.id.bottom_menu_title_icon).apply {
            setImageDrawable(
                ViewStateDrawables.selectedDefaultDrawableFromRes(
                    context,
                    intArrayOf(R.drawable.ic_menu_favorite, R.drawable.ic_menu_favorite_selected)
                )
            )
            isSelected = viewModel.uiState.value.isFavorite
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        state.music?.let { music = it }
                        favoriteButton?.isSelected = state.isFavorite
                    }
                }
                launch {
                    viewModel.events.collect(::handleEvent)
                }
            }
        }
    }

    fun onMusicChanged(updatedMusic: Music) {
        viewModel.onMusicChanged(updatedMusic)
    }

    private fun toggleFavorite() {
        val favoriteView = favoriteButton ?: return
        favoriteView.startAnimation(createFavoriteAnimation())
        viewModel.toggleFavorite()
    }

    private fun confirmDeleteTrack() {
        val activity = requireActivity()
        val targetMusic = music
        parentFragmentManager.setFragmentResultListener(
            DELETE_CONFIRM_RESULT_KEY,
            activity
        ) { _, bundle ->
            if (bundle.getBoolean(DeleteConfirmDialogFragment.RESULT_CONFIRMED, false)) {
                val deleteSourceFile = bundle.getBoolean(DeleteConfirmDialogFragment.RESULT_EXTRA_CHECKED, true)
                activity.lifecycleScope.launch {
                    val success = if (deleteSourceFile) {
                        activity.applicationContext.appDependencies.deleteTracksUseCase(listOf(targetMusic)) > 0
                    } else {
                        activity.applicationContext.appDependencies.trackMutationRepo
                            .removeTracksFromLibraryOnly(
                                trackIds = listOf(targetMusic.id),
                                stateTime = System.currentTimeMillis()
                            )
                        true
                    }
                    ToastUtil.show(
                        activity,
                        if (success) R.string.succeed else R.string.feature_not_implemented
                    )
                }
            }
        }

        DeleteConfirmDialogFragment.forTrackDelete(
            resultKey = DELETE_CONFIRM_RESULT_KEY,
            trackTitle = music.title
        ).show(parentFragmentManager, DeleteConfirmDialogFragment::class.java.simpleName)
        dismissAllowingStateLoss()
    }

    private fun handleEvent(event: MusicOptionsEvent) {
        when (event) {
            is MusicOptionsEvent.ShowToast -> {
                if (event.args.isEmpty()) {
                    ToastUtil.show(requireContext(), event.messageRes)
                } else {
                    ToastUtil.show(requireContext(), getString(event.messageRes, *event.args.toTypedArray()))
                }
            }
        }
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
