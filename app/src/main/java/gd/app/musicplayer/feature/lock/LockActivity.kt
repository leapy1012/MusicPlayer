package gd.app.musicplayer.feature.lock

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.text.format.DateFormat
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bumptech.glide.Glide
import com.coui.appcompat.poplist.COUIPopupListWindow
import com.coui.appcompat.poplist.PopupListItem
import dagger.hilt.android.AndroidEntryPoint
import gd.app.lib.model.image.SkinImageView
import gd.app.lib.model.lrc.view.LyricView
import gd.app.lib.view.DragDismissLayout
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.albumArtSource
import gd.app.musicplayer.core.common.extension.applyRoundedOutline
import gd.app.musicplayer.core.common.extension.isFavorite
import gd.app.musicplayer.core.common.extension.loadMusicArtworkLarge
import gd.app.musicplayer.core.common.extension.screenHeight
import gd.app.musicplayer.core.common.extension.screenWidth
import gd.app.musicplayer.core.common.extension.toDurationString
import gd.app.musicplayer.core.designsystem.image.AudioCover
import gd.app.musicplayer.feature.player.common.PlaybackProgressBinder
import gd.app.musicplayer.core.designsystem.dialog.MaterialDialogConfigFactory
import gd.app.musicplayer.core.designsystem.dialog.showMessageDialog
import gd.app.musicplayer.core.designsystem.view.SeekBar
import gd.app.musicplayer.core.datastore.SettingPreferencesDataStore
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.playback.PlaybackController
import gd.app.musicplayer.playback.lock.LockScreenController
import gd.app.musicplayer.playback.service.MusicPlaybackService
import gd.app.musicplayer.ui.common.base.BaseActivity
import gd.app.musicplayer.ui.common.menu.CouiPopupListSurface
import gd.app.musicplayer.ui.common.playback.PlayModeViewModel
import gd.app.musicplayer.feature.lyrics.setLyricText
import gd.app.musicplayer.feature.player.full.PlayerViewModel
import gd.app.musicplayer.playback.command.PlaybackServiceActions
import gd.app.musicplayer.util.LyricsLoader
import gd.app.musicplayer.util.TrackLyricsStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import jp.wasabeef.glide.transformations.BlurTransformation

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

    @Inject lateinit var settingPreferencesDataStore: SettingPreferencesDataStore
    @Inject lateinit var lockScreenController: LockScreenController
    @Inject lateinit var playbackController: PlaybackController
    @Inject lateinit var materialDialogConfigFactory: MaterialDialogConfigFactory

    private val playModeViewModel: PlayModeViewModel by viewModels()
    private val playerViewModel: PlayerViewModel by viewModels()

    private var currentTrack: Music? = null
    private var userSeeking = false
    private var dragDismissing = false

    private var clockJob: Job? = null
    private var lyricsJob: Job? = null

    private var lockBackgroundMode = LOCK_BACKGROUND_ARTWORK
    private var lastRenderedTrackId = NO_TRACK_ID
    private var lastRenderedArtworkKey: String? = null
    private var lockMorePopupMenu: LockMorePopupMenu? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        configureWindowForLockScreen()
        super.onCreate(savedInstanceState)
        configureWindowForLockScreen()

        setContentView(R.layout.activity_lock)

        configureTransparentContentRoot()
        disableBackButton()
        bindViews()
        configureDragDismiss()
        bindListeners()
        renderCurrentPlaybackSnapshot()
        observePlayback()
        observeLockscreenSettings()
        observePlayMode()
    }

    override fun onStart() {
        super.onStart()
        startClock()
    }

    override fun onStop() {
        stopClock()
        super.onStop()
    }

    override fun onDestroy() {
        lockMorePopupMenu?.dismiss()
        lockMorePopupMenu = null
        lyricsJob?.cancel()
        lyricsJob = null
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        applyShowWhenLockedPolicy()
        renderCurrentPlaybackSnapshot()
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.lock_more -> showLockMoreMenu(view)
            R.id.lock_play_favourite -> toggleFavorite()
            R.id.lock_play_queue -> LockPlaybackQueueDialogFragment.show(supportFragmentManager)
            R.id.control_mode -> cyclePlayMode()
            R.id.control_previous -> playerViewModel.playPrevious(this)
            R.id.control_play_pause -> playerViewModel.onPrimaryPlayPauseClicked(this)
            R.id.control_next -> playerViewModel.playNext(this)
        }
    }

    override fun onProgressChanged(
        seekBar: SeekBar,
        progress: Int,
        fromUser: Boolean
    ) {
        if (!fromUser) return

        val progressMs = progress.toLong()
        currentTimeView.text = progressMs.toDurationString()
        lyricView.setCurrentTime(progressMs)
        playerViewModel.seekTo(this, progress)
    }

    override fun onStartTrackingTouch(seekBar: SeekBar) {
        userSeeking = true
        dragDismissLayout.setDisallowDragIntercept(true)
    }

    override fun onStopTrackingTouch(seekBar: SeekBar) {
        userSeeking = false
        dragDismissLayout.setDisallowDragIntercept(false)
    }

    override fun onDismissed(view: View?) {
        /*
         * Do not hide the view before finish.
         * Hiding the content exposes the Activity/window background and causes
         * the black flash on lock screen.
         */
        finish()
        overridePendingTransition(0, 0)
    }

    private fun configureWindowForLockScreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)

        window.setBackgroundDrawableResource(android.R.color.transparent)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)

        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

        applyShowWhenLockedPolicy()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    /**
     * Matches original [com.ijoysoft.music.activity.BaseLockActivity.n1]:
     * show-when-locked always; turn-screen-on only pre-O; dismiss keyguard only if insecure.
     */
    private fun applyShowWhenLockedPolicy() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
            return
        }

        runCatching {
            val keyguardManager =
                getSystemService(Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager
                    ?: return
            if (keyguardManager.isKeyguardSecure) return
            keyguardManager.requestDismissKeyguard(this, null)
        }
    }

    private fun configureTransparentContentRoot() {
        window.decorView.setBackgroundColor(Color.TRANSPARENT)
        findViewById<View>(android.R.id.content).setBackgroundColor(Color.TRANSPARENT)
    }

    private fun disableBackButton() {
        onBackPressedDispatcher.addCallback(this) {
            // Intentionally disabled for lock screen behavior.
        }
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

        albumImage.applyRoundedOutline(R.dimen.item_image_corner_radius)
    }

    private fun configureDragDismiss() {
        dragDismissLayout.setOnDismissListener(this)

        dragDismissLayout.setAllowedDragDirections(
            DragDismissLayout.DIRECTION_LEFT or
                    DragDismissLayout.DIRECTION_RIGHT or
                    DragDismissLayout.DIRECTION_UP
        )

        dragDismissLayout.setOnDragStateListener(
            object : DragDismissLayout.OnDragStateListener {
                override fun onDragStarted() {
                    dragDismissing = true
                }

                override fun onDragProgress(progress: Float) = Unit

                override fun onDragFinished(dismissed: Boolean) {
                    dragDismissing = false
                    if (!dismissed) {
                        renderCurrentPlaybackSnapshot()
                        currentTrack?.let(::loadLyrics)
                        renderClockNow()
                    }
                }
            }
        )
    }

    private fun bindListeners() {
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
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                playerViewModel.playbackState.collect { state ->
                    val track = state.currentTrack ?: run {
                        finish()
                        return@collect
                    }

                    if (dragDismissing) return@collect

                    renderPlaybackState(
                        track = track,
                        isPlaying = state.isPlaying,
                        positionMs = state.positionMs
                    )
                }
            }
        }
    }

    private fun renderCurrentPlaybackSnapshot() {
        val state = playerViewModel.playbackState.value
        val track = state.currentTrack ?: run {
            finish()
            return
        }

        renderPlaybackState(
            track = track,
            isPlaying = state.isPlaying,
            positionMs = state.positionMs
        )
        playModeView.setImageResource(playModeViewModel.uiState.value.iconRes)
    }

    private fun renderPlaybackState(
        track: Music,
        isPlaying: Boolean,
        positionMs: Long
    ) {
        val trackChanged = currentTrack?.id != track.id
        val artworkKey = resolveArtworkKey(track)
        val artworkChanged = lastRenderedArtworkKey != artworkKey

        currentTrack = track

        if (trackChanged) {
            renderTrackInfo(track)
            loadLyrics(track)
            lastRenderedTrackId = track.id
        }

        favoriteView.isSelected = track.isFavorite()
        playPauseView.isSelected = isPlaying

        if (!userSeeking) {
            renderProgress(
                positionMs = positionMs,
                durationMs = track.duration
            )
        }

        if (trackChanged || artworkChanged) {
            renderArtwork(track)
            lastRenderedArtworkKey = artworkKey
        }
    }

    private fun renderTrackInfo(track: Music) {
        titleView.text = track.title
        artistView.text = track.artist.ifBlank {
            getString(android.R.string.unknownName)
        }

        val metrics = PlaybackProgressBinder.metrics(
            durationMs = track.duration.toLong(),
            positionMs = 0L
        )
        totalTimeView.text = metrics.durationMs.toDurationString()
        progressView.setMax(metrics.durationInt)
        progressView.isEnabled = track.duration > 0
    }

    private fun renderProgress(
        positionMs: Long,
        durationMs: Int
    ) {
        val metrics = PlaybackProgressBinder.metrics(
            durationMs = durationMs.toLong(),
            positionMs = positionMs,
            minDurationMs = 0L
        )

        progressView.setProgress(metrics.positionInt)
        currentTimeView.text = metrics.positionMs.toDurationString()
        lyricView.setCurrentTime(metrics.positionMs)
    }

    private fun renderArtwork(track: Music) {
        albumImage.loadMusicArtworkLarge(track.albumArtSource())
        updateBackground(track)
    }

    private fun updateBackground(track: Music) {
        if (lockBackgroundMode == LOCK_BACKGROUND_ARTWORK) {
            Glide.with(this)
                .asBitmap()
                .load(resolveArtworkSource(track))
                .override(
                    (screenWidth / LOCK_BACKGROUND_SAMPLE_WIDTH_DIVISOR).coerceAtLeast(1),
                    (screenHeight / LOCK_BACKGROUND_SAMPLE_HEIGHT_DIVISOR).coerceAtLeast(1)
                )
                .placeholder(backgroundImage.drawable)
                .error(ColorDrawable(Color.TRANSPARENT))
                .transform(BlurTransformation(LOCK_BACKGROUND_BLUR_RADIUS, 1))
                .into(backgroundImage)
        } else {
            Glide.with(this).clear(backgroundImage)
            backgroundImage.setImageDrawable(
                themeRepo.getCorePalette().getActivityBackgroundDrawable(this)
            )
        }
    }

    private fun resolveArtworkSource(track: Music): Any {
        return track.albumArtSource()
    }

    private fun resolveArtworkKey(track: Music): String {
        return when (val source = track.albumArtSource()) {
            is AudioCover -> source.source
            else -> source.toString().ifBlank { track.id.toString() }
        }
    }

    private fun observeLockscreenSettings() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                settingPreferencesDataStore.observeSettingPreferences().collect { preferences ->
                    val backgroundMode = preferences.lockscreen.backgroundMode

                    if (lockBackgroundMode == backgroundMode) return@collect

                    lockBackgroundMode = backgroundMode

                    if (!dragDismissing) {
                        currentTrack?.let(::updateBackground)
                    }
                }
            }
        }
    }

    private fun observePlayMode() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                playModeViewModel.uiState.collect { state ->
                    if (!dragDismissing) {
                        playModeView.setImageResource(state.iconRes)
                    }
                }
            }
        }
    }

    private fun loadLyrics(track: Music) {
        lyricsJob?.cancel()

        lyricView.setTimeOffset(
            TrackLyricsStore.from(this).getTrackLyricOffset(track.id)
        )
        lyricView.setLyricText(null)

        lyricsJob = lifecycleScope.launch {
            val result = LyricsLoader.load(
                this@LockActivity,
                track.id,
                track.data
            )

            if (currentTrack?.id != track.id) return@launch
            if (dragDismissing) return@launch

            lyricView.setTimeOffset(
                TrackLyricsStore.from(this@LockActivity).getTrackLyricOffset(track.id)
            )
            lyricView.setLyricText(result.text)
            lyricView.setCurrentTime(playerViewModel.playbackState.value.positionMs)
        }
    }

    private fun startClock() {
        if (clockJob?.isActive == true) return

        clockJob = lifecycleScope.launch {
            while (isActive) {
                if (!dragDismissing) {
                    renderClockNow()
                }
                delay(CLOCK_TICK_MS)
            }
        }
    }

    private fun renderClockNow() {
        val now = Date()

        timeView.text = SimpleDateFormat(
            CLOCK_TIME_PATTERN,
            Locale.getDefault()
        ).format(now)

        dateView.text = DateFormat.format(
            CLOCK_DATE_PATTERN,
            now
        )
    }

    private fun stopClock() {
        clockJob?.cancel()
        clockJob = null
    }

    private fun cyclePlayMode() {
        playModeViewModel.cyclePlayMode()
    }

    private fun toggleFavorite() {
        if (currentTrack == null) return
        playbackController.toggleFavorite()
    }

    private fun showLockMoreMenu(anchor: View) {
        lockMorePopupMenu?.dismiss()
        lockMorePopupMenu = LockMorePopupMenu(
            context = this,
            onTurnOffLockScreen = ::showTurnOffLockScreenDialog,
            onQuit = ::quitApplication
        ).also { menu ->
            menu.show(anchor)
        }
    }

    private fun showTurnOffLockScreenDialog() {
        showMessageDialog(
            materialDialogConfigFactory.createMaterialMessageDialogConfig(this).apply {
                titleText = getString(R.string.lock_dialog_title)
                messageText = getString(R.string.lock_dialog_msg)
                negativeButtonText = getString(R.string.cancel)
                positiveButtonText = getString(R.string.turn_off)
                positiveButtonClickListener = DialogInterface.OnClickListener { dialog, _ ->
                    lifecycleScope.launch {
                        settingPreferencesDataStore.updateLockScreenEnabled(false)
                        lockScreenController.refresh(false)
                        dialog.dismiss()
                        finish()
                    }
                }
                onShowListener = DialogInterface.OnShowListener { dialog ->
                    applyThemeTo((dialog as? android.app.Dialog)?.window?.decorView)
                }
            }
        )
    }

    private fun quitApplication() {
        val appContext = applicationContext
        appContext.startService(
            Intent(appContext, MusicPlaybackService::class.java).apply {
                action = PlaybackServiceActions.ACTION_EXIT
            }
        )
        finishAffinity()

        Handler(Looper.getMainLooper()).postDelayed(
            {
                Process.killProcess(Process.myPid())
            },
            QUIT_KILL_PROCESS_DELAY_MS
        )
    }

    companion object {
        private const val LOCK_BACKGROUND_ARTWORK = 1
        private const val NO_TRACK_ID = Long.MIN_VALUE
        private const val LOCK_BACKGROUND_BLUR_RADIUS = 40
        private const val LOCK_BACKGROUND_SAMPLE_WIDTH_DIVISOR = 7
        private const val LOCK_BACKGROUND_SAMPLE_HEIGHT_DIVISOR = 10
        private const val QUIT_KILL_PROCESS_DELAY_MS = 150L

        private const val CLOCK_TICK_MS = 1_000L
        private const val CLOCK_TIME_PATTERN = "HH:mm"
        private const val CLOCK_DATE_PATTERN = "EEE, MMM d"

        fun start(context: Context) {
            val intent = Intent(context, LockActivity::class.java).apply {
                // Original LockActivity.r1 flags: NEW_TASK | EXCLUDE_FROM_RECENTS (0x10800000).
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                )
            }
            runCatching {
                context.startActivity(intent)
            }.onFailure {
                context.applicationContext.startActivity(intent)
            }
        }
    }
}

private class LockMorePopupMenu(
    private val context: Context,
    private val onTurnOffLockScreen: () -> Unit,
    private val onQuit: () -> Unit
) {

    private var popup: COUIPopupListWindow? = null

    fun show(anchor: View) {
        dismiss()
        val actions = listOf(
            R.string.lock_dialog_title to onTurnOffLockScreen,
            R.string.adv_quit to onQuit
        )
        val items = ArrayList(
            actions.map { (titleRes, _) ->
                PopupListItem.Builder()
                    .setId(titleRes)
                    .setTitle(context.getString(titleRes))
                    .setIsEnable(true)
                    .build()
            }
        )
        CouiPopupListSurface.paintItemTitles(context, items)
        val window = COUIPopupListWindow(
            CouiPopupListSurface.popupContext(context)
        ).also { popup = it }
        window.setItemList(items)
        window.setOnItemClickListener { _, _, position, _ ->
            dismiss()
            actions.getOrNull(position)?.second?.invoke()
        }
        window.show(anchor)
        CouiPopupListSurface.apply(window, context)
        anchor.post {
            if (popup === window) CouiPopupListSurface.apply(window, context)
        }
    }

    fun dismiss() {
        popup?.dismiss()
        popup = null
    }
}
