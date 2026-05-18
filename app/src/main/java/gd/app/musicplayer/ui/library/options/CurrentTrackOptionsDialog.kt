package gd.app.musicplayer.ui.library.options

import android.content.Context
import android.media.AudioManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.parcelable
import gd.app.musicplayer.core.designsystem.dialog.MaterialDialogConfigFactory
import gd.app.musicplayer.core.designsystem.drawable.DrawableUtil
import gd.app.musicplayer.core.designsystem.theme.accentColor
import gd.app.musicplayer.core.common.util.ToastUtil
import gd.app.musicplayer.domain.model.ArtworkRequest
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.ui.playlist.PlaylistSelectActivity
import gd.app.musicplayer.ui.selection.MusicShareSupport
import gd.app.musicplayer.ui.sleep.SleepActivity
import gd.app.musicplayer.ui.tags.EditTagsActivity
import gd.app.musicplayer.ui.common.base.BaseBottomGridMenuDialog
import gd.app.musicplayer.core.designsystem.view.SeekBar
import gd.app.musicplayer.ui.library.albums.AlbumMusicActivity
import gd.app.musicplayer.ui.library.artwork.ManageArtworkDialogFragment
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

@AndroidEntryPoint
class CurrentTrackOptionsDialog : BaseBottomGridMenuDialog() {

    private lateinit var music: Music
    private val viewModel: CurrentTrackOptionsViewModel by viewModels()
    private lateinit var audioManager: AudioManager
    private var volumeSeekBar: SeekBar? = null
    private var volumeText: TextView? = null
    private var volumeIcon: ImageView? = null
    private var lastNonZeroVolume = 0

    @Inject
    lateinit var materialDialogConfigFactory: MaterialDialogConfigFactory

    private val deleteConfirmResultKey: String
        get() = "current_track_delete_confirm_${music.id}"

    override fun onReadArguments(arguments: Bundle) {
        music = arguments.parcelable(ARG_MUSIC) ?: error("Missing music")
        viewModel.initialize(music)
    }

    override fun provideMenuItems(): List<MenuItem> = buildList {
        add(MenuItem.create(R.string.add_to, R.drawable.ic_menu_add))
        add(MenuItem.create(R.string.dlg_more_view_artist, R.drawable.ic_more_artist))
        add(MenuItem.create(R.string.dlg_more_view_album, R.drawable.ic_more_album))
        add(MenuItem.create(R.string.dlg_manage_artwork, R.drawable.ic_menu_artwork))
        add(MenuItem.create(R.string.sleep_timer_2, R.drawable.ic_menu_sleep).withLabel(viewModel.uiState.value.sleepMenuLabel.ifBlank { getString(R.string.sleep_timer_2) }))
        add(MenuItem.create(R.string.dlg_ringtone_2, R.drawable.ic_menu_ringtone))
        add(MenuItem.create(R.string.hide_music, R.drawable.ic_menu_hide_music))
        add(MenuItem.create(R.string.delete, R.drawable.ic_menu_delete))
    }

    override fun onMenuItemClicked(item: MenuItem) {
        if (item.id == R.string.delete) {
            confirmDeleteTrack()
            return
        }

        dismiss()
        when (item.id) {
            R.string.add_to -> viewModel.onAddToClicked()
            R.string.dlg_more_view_artist -> viewModel.onViewArtistClicked()
            R.string.dlg_more_view_album -> viewModel.onViewAlbumClicked()
            R.string.dlg_manage_artwork -> {
                ManageArtworkDialogFragment.newInstance(ArtworkRequest.Track(music))
                    .show(parentFragmentManager, ManageArtworkDialogFragment::class.java.simpleName)
            }
            R.string.sleep_timer_2 -> SleepActivity.start(requireContext())
            R.string.dlg_ringtone_2 -> RingtoneActionHandler.handle(
                requireActivity(),
                music,
                materialDialogConfigFactory
            )
            R.string.hide_music -> viewModel.hideTrack()
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

        container.findViewById<ImageView>(R.id.bottom_menu_title_icon).apply {
            tag = "dialogTitleIcon"
            setImageResource(R.drawable.ic_menu_song_share)
            val inset = context.dp(10f)
            setPadding(inset, inset, inset, inset)
            setOnClickListener {
                dismiss()
                MusicShareSupport.share(requireContext(), listOf(music))
            }
        }

        container.findViewById<ImageView>(R.id.bottom_menu_title_icon_2).apply {
            tag = "dialogTitleIcon"
            setImageResource(R.drawable.ic_menu_song_detail)
            val inset = context.dp(10f)
            setPadding(inset, inset, inset, inset)
            setOnClickListener {
                dismiss()
                MusicDetailDialogFragment.newInstance(music)
                    .show(parentFragmentManager, MusicDetailDialogFragment::class.java.simpleName)
            }
        }
    }

    override fun onCreateBottomArea(inflater: LayoutInflater, container: LinearLayout) {
        audioManager = requireContext().getSystemService(Context.AUDIO_SERVICE) as AudioManager
        inflater.inflate(R.layout.layout_bottom_menu_volume, container, true)
        volumeText = container.findViewById(R.id.dialog_volume_text)
        volumeIcon = container.findViewById<ImageView>(R.id.dialog_volume_icon).apply {
            setImageResource(R.drawable.vector_bottom_menu_volume_selector)
            setOnClickListener { toggleMute() }
        }
        volumeSeekBar = container.findViewById<SeekBar>(R.id.dialog_seek_bar).apply {
            setMax(audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC))
            val accentColor = themeEngine.currentTheme().accentColor
            setProgressDrawable(
                DrawableUtil.roundedProgress(
                    backgroundColor = 0x33FFFFFF,
                    progressColor = accentColor,
                    cornerRadius = context.dp(8f)
                )
            )
            setThumbColor(accentColor)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, progress, 0)
                    }
                    if (progress > 0) lastNonZeroVolume = progress
                    renderVolume(progress)
                }

                override fun onStopTrackingTouch(seekBar: SeekBar) {
                    setRecyclerViewScrollBlocked(false)
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) {
                    setRecyclerViewScrollBlocked(true)
                }
            })
        }
        syncVolumeViews()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        state.music?.let { music = it }
                        refreshMenuItems()
                    }
                }
                launch {
                    viewModel.events.collect(::handleEvent)
                }
            }
        }
    }

    private fun toggleMute() {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val target = if (current == 0) lastNonZeroVolume.takeIf { it > 0 } ?: (max / 2).coerceAtLeast(1) else 0
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
        syncVolumeViews()
    }

    private fun syncVolumeViews() {
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        if (current > 0) lastNonZeroVolume = current
        volumeSeekBar?.setProgress(current)
        renderVolume(current)
    }

    private fun renderVolume(volume: Int) {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val percent = ((volume / max.toFloat()) * 100f).roundToInt()
        volumeText?.text = "$percent%"
        volumeIcon?.isSelected = volume == 0
    }

    private fun confirmDeleteTrack() {
        DeleteConfirmDialogFragment.forTrackDelete(
            resultKey = deleteConfirmResultKey,
            trackTitle = music.title,
            music = music
        ).show(parentFragmentManager, DeleteConfirmDialogFragment::class.java.simpleName)
        dismissAllowingStateLoss()
    }

    private fun handleEvent(event: CurrentTrackOptionsEvent) {
        when (event) {
            is CurrentTrackOptionsEvent.ShowToast -> ToastUtil.show(requireContext(), event.messageRes)
            is CurrentTrackOptionsEvent.OpenAddTo -> PlaylistSelectActivity.start(requireContext(), event.tracks)
            is CurrentTrackOptionsEvent.OpenAlbum -> AlbumMusicActivity.start(requireContext(), event.album)
            is CurrentTrackOptionsEvent.OpenArtist -> AlbumMusicActivity.start(requireContext(), event.artist)
            CurrentTrackOptionsEvent.Dismiss -> dismissAllowingStateLoss()
        }
    }

    private fun Context.dp(value: Float): Int = (value * resources.displayMetrics.density).roundToInt()

    companion object {
        private const val ARG_MUSIC = "music"

        fun newInstance(music: Music): CurrentTrackOptionsDialog {
            return CurrentTrackOptionsDialog().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_MUSIC, music)
                }
            }
        }
    }
}

