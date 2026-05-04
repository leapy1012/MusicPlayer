package gd.app.musicplayer.ui.feature.lock

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import dagger.hilt.android.AndroidEntryPoint
import gd.app.lib.model.image.SkinImageView
import gd.app.lib.model.lrc.view.LyricView
import gd.app.lib.view.DragDismissLayout
import gd.app.musicplayer.R
import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.data.model.MusicSet
import gd.app.musicplayer.core.extension.isFavorite
import gd.app.musicplayer.core.extension.loadMusicArtwork
import gd.app.musicplayer.core.extension.toDurationString
import gd.app.musicplayer.ui.feature.library.MusicOptionsDialog
import gd.app.musicplayer.ui.common.playback.PlayModeViewModel
import gd.app.musicplayer.ui.player.PlayerViewModel
import gd.app.musicplayer.ui.common.base.BaseActivity
import gd.app.musicplayer.ui.common.base.PlaybackQueueBottomSheetFragment
import gd.app.musicplayer.core.ui.view.SeekBar
import gd.app.musicplayer.util.PreferenceUtil
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.toDuration

@AndroidEntryPoint
class LockActivity : BaseActivity(),
    View.OnClickListener,
    SeekBar.OnSeekBarChangeListener,
    DragDismissLayout.OnDismissListener {

    private lateinit var dragDismissLayout: DragDismissLayout
    private lateinit var backgroundImage: SkinImageView
    private lateinit var albumImage: ImageView
    private lateinit var timeView: TextView
    private lateinit var dateView: TextView
    private lateinit var titleView: TextView
    private lateinit var artistView: TextView
    private lateinit var lyricView: LyricView
    private lateinit var playModeView: ImageView
    private lateinit var playPauseView: ImageView
    private lateinit var favoriteView: ImageView
    private lateinit var progressView: SeekBar
    private lateinit var currentTimeView: TextView
    private lateinit var totalTimeView: TextView

    private val preferenceUtil by lazy { PreferenceUtil.getInstance(this) }
    private val toggleFavoriteTrack by lazy { appDependencies.toggleFavoriteTrackUseCase }
    private val playModeViewModel: PlayModeViewModel by viewModels()
    private val playerViewModel: PlayerViewModel by viewModels()

    private var currentTrack: Music? = null
    private var userSeeking = false
    private var clockJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureWindow()
        setContentView(R.layout.activity_lock)

        onBackPressedDispatcher.addCallback(this) {
            // Disabled to match the original lock screen behavior.
        }

        bindViews()
        bindListeners()
        observePlayback()
        observePlayMode()
    }

    override fun onStart() {
        super.onStart()
        startClock()
    }

    override fun onStop() {
        clockJob?.cancel()
        clockJob = null
        super.onStop()
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.lock_more -> showTrackOptions()
            R.id.lock_play_favourite -> toggleFavorite()
            R.id.lock_play_queue -> PlaybackQueueBottomSheetFragment.show(supportFragmentManager)
            R.id.control_mode -> cyclePlayMode()
            R.id.control_previous -> playerViewModel.playPrevious(this)
            R.id.control_play_pause -> {
//                val state = playerViewModel.playbackState.value
//                if (state.queue.isEmpty()) {
//                    lifecycleScope.launch {
//                        playerViewModel.playAllTracks(this@LockActivity)
//                    }
//                } else {
//                    playerViewModel.togglePlayPause(this)
//                }
            }
            R.id.control_next -> playerViewModel.playNext(this)
        }
    }

    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
        if (!fromUser) return
        currentTimeView.text = progress.toLong().toDurationString()
        lyricView.setCurrentTime(progress.toLong())
        playerViewModel.seekTo(this, progress)
    }

    override fun onStartTrackingTouch(seekBar: SeekBar) {
        userSeeking = true
        dragDismissLayout.setDisallowInterceptTouchEvent(true)
    }

    override fun onStopTrackingTouch(seekBar: SeekBar) {
        userSeeking = false
        dragDismissLayout.setDisallowInterceptTouchEvent(false)
    }

    override fun onDismissed(view: View) {
        view.visibility = View.GONE
        finish()
    }

    private fun configureWindow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
    }

    private fun bindViews() {
        dragDismissLayout = findViewById(R.id.pull)
        backgroundImage = findViewById(R.id.lock_play_skin)
        albumImage = findViewById(R.id.lock_play_album)
        timeView = findViewById(R.id.lock_time)
        dateView = findViewById(R.id.lock_date)
        titleView = findViewById(R.id.lock_play_title)
        artistView = findViewById(R.id.lock_play_artist)
        lyricView = findViewById(R.id.lock_play_lrc)
        playModeView = findViewById(R.id.control_mode)
        playPauseView = findViewById(R.id.control_play_pause)
        favoriteView = findViewById(R.id.lock_play_favourite)
        currentTimeView = findViewById(R.id.lock_curr_time)
        totalTimeView = findViewById(R.id.lock_total_time)
        progressView = findViewById(R.id.lock_progress)
    }

    private fun bindListeners() {
        dragDismissLayout.setOnDismissListener(this)
        progressView.setOnSeekBarChangeListener(this)
        listOf(
            R.id.lock_more,
            R.id.lock_play_favourite,
            R.id.lock_play_queue,
            R.id.control_mode,
            R.id.control_previous,
            R.id.control_play_pause,
            R.id.control_next
        ).forEach { viewId ->
            findViewById<View>(viewId).setOnClickListener(this)
        }
    }

    private fun observePlayback() {
//        lifecycleScope.launch {
//            playerViewModel.playbackState.collect { state ->
//                val track = state.currentTrack ?: run {
//                    finish()
//                    return@collect
//                }
//                currentTrack = track
//                titleView.text = track.title
//                artistView.text = track.artist.ifBlank { getString(android.R.string.unknownName) }
//                favoriteView.isSelected = track.isFavorite()
//                playPauseView.isSelected = state.isPlaying
//                totalTimeView.text = track.duration.toLong().toDurationString()
//                progressView.setMax(track.duration.coerceAtLeast(1))
//                progressView.isEnabled = !track.data.isNullOrBlank()
//                if (!userSeeking) {
//                    progressView.setProgress(state.positionMs.coerceAtLeast(0L).toInt())
//                    currentTimeView.text = state.positionMs.toDurationString()
//                }
//                lyricView.setCurrentTime(state.positionMs)
//                track.loadMusicArtwork(albumImage)
//                updateBackground(track)
//            }
//        }
    }

    private fun updateBackground(track: Music) {
        if (preferenceUtil.getIntPreference(KEY_LOCK_BACKGROUND, 1) == 1) {
            Glide.with(this)
                .load(
                    track.albumPicture?.takeIf { it.isNotBlank() }
                        ?: track.albumId.takeIf { it.isNotBlank() }
                            ?.let { "content://media/external/audio/albumart/$it" }
                        ?: track.data
                )
                .placeholder(R.drawable.th_music_large)
                .error(R.drawable.th_music_large)
                .centerCrop()
                .into(backgroundImage)
        } else {
            backgroundImage.setImageDrawable(
                appDependencies.themeRepo.getCorePalette(this).getActivityBackgroundDrawable(this)
            )
        }
    }

    private fun startClock() {
        if (clockJob?.isActive == true) return
        clockJob = lifecycleScope.launch {
            while (isActive) {
                val now = Date()
                val is24Hour = when (preferenceUtil.getLockScreenTimeFormat()) {
                    1 -> false
                    2 -> true
                    else -> DateFormat.is24HourFormat(this@LockActivity)
                }
                val timePattern = if (is24Hour) "HH:mm" else "hh:mm"
                timeView.text = SimpleDateFormat(timePattern, Locale.getDefault()).format(now)
                dateView.text = DateFormat.format("EEE, MMM d", now)
                delay(1_000L)
            }
        }
    }

    private fun cyclePlayMode() {
        playModeViewModel.cyclePlayMode()
    }

    private fun observePlayMode() {
        lifecycleScope.launch {
            playModeViewModel.uiState.collect { state ->
                playModeView.setImageResource(state.iconRes)
            }
        }
    }

    private fun toggleFavorite() {
        val track = currentTrack ?: return
        lifecycleScope.launch {
            favoriteView.isSelected = toggleFavoriteTrack(track.id)
        }
    }

    private fun showTrackOptions() {
        val track = currentTrack ?: return
        MusicOptionsDialog.newInstance(track, MusicSet.Tracks)
            .show(supportFragmentManager, MusicOptionsDialog::class.java.simpleName)
    }

    companion object {
        private const val KEY_LOCK_BACKGROUND = "lock_background"

        fun start(context: Context) {
            context.startActivity(
                Intent(context, LockActivity::class.java).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                    )
                }
            )
        }
    }
}


