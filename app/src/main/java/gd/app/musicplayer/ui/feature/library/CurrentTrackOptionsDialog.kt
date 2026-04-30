package gd.app.musicplayer.ui.feature.library

import android.app.AlertDialog
import android.content.Context
import android.media.AudioManager
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.setPadding
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.core.extension.parcelable
import gd.app.musicplayer.core.ui.drawable.DrawableUtil
import gd.app.musicplayer.core.util.ToastUtil
import gd.app.musicplayer.data.model.ArtworkRequest
import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.data.model.MusicSet
import gd.app.musicplayer.ui.feature.playlist.ActivityPlaylistSelect
import gd.app.musicplayer.ui.feature.selection.MusicShareSupport
import gd.app.musicplayer.ui.feature.sleep.SleepActivity
import gd.app.musicplayer.ui.feature.tags.EditTagsActivity
import gd.app.musicplayer.ui.common.base.BaseBottomGridMenuDialog
import gd.app.musicplayer.core.ui.view.SeekBar
import kotlinx.coroutines.launch
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

    override fun onReadArguments(arguments: Bundle) {
        music = arguments.parcelable(ARG_MUSIC) ?: error("Missing music")
        viewModel.initialize(music)
    }

    override fun provideMenuItems(): List<MenuItem> = buildList {
        add(MenuItem.create(R.string.add_to, R.drawable.ic_menu_add))
        add(MenuItem.create(R.string.dlg_more_view_artist, R.drawable.ic_more_artist))
        add(MenuItem.create(R.string.dlg_more_view_album, R.drawable.ic_more_album))
        add(MenuItem.create(R.string.dlg_manage_artwork, R.drawable.ic_menu_artwork))
        add(MenuItem.create(R.string.sleep_timer_2, R.drawable.vector_left_menu_sleep).withLabel(viewModel.uiState.value.sleepMenuLabel.ifBlank { getString(R.string.sleep_timer_2) }))
        add(MenuItem.create(R.string.dlg_ringtone_2, R.drawable.ic_menu_ringtone))
        add(MenuItem.create(R.string.hide_music, R.drawable.ic_menu_hide_folder))
        add(MenuItem.create(R.string.delete, R.drawable.ic_menu_delete))
    }

    override fun onMenuItemClicked(item: MenuItem) {
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
            R.string.dlg_ringtone_2 -> ToastUtil.show(requireContext(), R.string.feature_not_implemented)
            R.string.hide_music -> viewModel.hideTrack()
            R.string.delete -> confirmDeleteTrack()
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
            setImageResource(R.drawable.ic_menu_share)
            setOnClickListener {
                dismiss()
                MusicShareSupport.share(requireContext(), listOf(music))
            }
        }

        container.findViewById<ImageView>(R.id.bottom_menu_title_icon_2).apply {
            tag = "dialogTitleIcon"
            setImageResource(R.drawable.ic_menu_song_detail)
            setOnClickListener {
                dismiss()
                MusicDetailDialogFragment.newInstance(music)
                    .show(parentFragmentManager, MusicDetailDialogFragment::class.java.simpleName)
            }
        }
    }

    override fun onCreateBottomArea(inflater: LayoutInflater, container: LinearLayout) {
        audioManager = requireContext().getSystemService(Context.AUDIO_SERVICE) as AudioManager
        container.addView(createVolumeDivider(container.context))
        container.addView(createVolumeRow(container.context))
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

    private fun createVolumeDivider(context: Context): View {
        return View(context).apply {
            tag = "dialogDivider"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                context.dp(1f)
            )
        }
    }

    private fun createVolumeRow(context: Context): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(context.dp(16f), context.dp(8f), context.dp(16f), context.dp(12f))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                context.dp(56f)
            )
        }

        volumeIcon = ImageView(context).apply {
            tag = "dialogVolumeIcon"
            setImageResource(android.R.drawable.ic_lock_silent_mode_off)
            setPadding(context.dp(10f))
            layoutParams = LinearLayout.LayoutParams(context.dp(40f), context.dp(40f))
            setOnClickListener { toggleMute() }
        }

        volumeSeekBar = SeekBar(context).apply {
            tag = "dialogSeekBar"
            setMax(audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC))
            setThumb(DrawableUtil.gradientDrawable(context.dp(100f).toFloat(), 0xFFFFFFFF.toInt()))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, progress, 0)
                    }
                    if (progress > 0) lastNonZeroVolume = progress
                    renderVolume(progress)
                }

                override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
                override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
            })
            layoutParams = LinearLayout.LayoutParams(0, context.dp(28f), 1f).apply {
                marginStart = context.dp(8f)
                marginEnd = context.dp(12f)
            }
        }

        volumeText = TextView(context).apply {
            tag = "dialogVolumeText"
            textSize = 14f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(context.dp(48f), LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        row.addView(volumeIcon)
        row.addView(volumeSeekBar)
        row.addView(volumeText)
        return row
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
        volumeIcon?.setImageResource(
            if (volume == 0) android.R.drawable.ic_lock_silent_mode else android.R.drawable.ic_lock_silent_mode_off
        )
    }

    private fun confirmDeleteTrack() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.delete)
            .setMessage(music.title)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                viewModel.deleteTrack()
            }
            .show()
    }

    private fun handleEvent(event: CurrentTrackOptionsEvent) {
        when (event) {
            is CurrentTrackOptionsEvent.ShowToast -> ToastUtil.show(requireContext(), event.messageRes)
            is CurrentTrackOptionsEvent.OpenAddTo -> ActivityPlaylistSelect.start(requireContext(), event.tracks)
            is CurrentTrackOptionsEvent.OpenAlbum -> AlbumMusicActivity.start(requireContext(), event.album)
            is CurrentTrackOptionsEvent.OpenArtist -> AlbumMusicActivity.start(requireContext(), event.artist)
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
