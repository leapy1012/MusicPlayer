package gd.app.musicplayer.playback

import android.app.Notification
import android.app.Notification.Action
import android.app.Notification.MediaStyle
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.ForegroundServiceStartNotAllowedException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.MediaDescription
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import gd.app.musicplayer.R
import gd.app.musicplayer.core.ui.extension.appContainer
import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.data.model.albumArtSource
import gd.app.musicplayer.feature.lock.LockActivity
import gd.app.musicplayer.feature.widget.provider.WidgetRenderer
import gd.app.musicplayer.ui.shell.MainActivity
import gd.app.musicplayer.util.PreferenceUtil
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.random.Random

class MusicPlayService : Service() {
    private lateinit var player: ExoPlayer
    private lateinit var notificationManager: NotificationManager
    private lateinit var mediaSession: MediaSession
    private lateinit var defaultArtwork: Bitmap
    private lateinit var serviceScope: CoroutineScope

    private val progressHandler = Handler(Looper.getMainLooper())
    private val progressTicker = object : Runnable {
        override fun run() {
            maybeHandleTimedTransition()
            publishState()
            progressHandler.postDelayed(this, PROGRESS_TICK_MS)
        }
    }

    private var artworkTarget: CustomTarget<Bitmap>? = null
    private var queue: List<Music> = emptyList()
    private var currentIndex = -1
    private var hasAudioFocus = false
    private var isForegroundStarted = false
    private var stopAfterCurrentTrack = false
    private var currentArtwork: Bitmap? = null
    private var currentArtworkTrackId = Long.MIN_VALUE
    private var lastStartedTrackId = Long.MIN_VALUE
    private var shouldResumeAfterTransientLoss = false
    private var fadeAnimationId = 0L
    private var timedTransitionTrackId = Long.MIN_VALUE
    private var timedTransitionStartedAtMs = 0L
    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            if (intent?.action != Intent.ACTION_SCREEN_OFF) return
            if (currentIndex !in queue.indices) return
            if (!PreferenceUtil.getInstance(context).isLockScreenEnabled(false)) return
            LockActivity.start(applicationContext)
        }
    }

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        if (PreferenceUtil.getInstance(this).getBooleanPreference(KEY_SIMULTANEOUS_PLAY, false)) {
            return@OnAudioFocusChangeListener
        }
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                shouldResumeAfterTransientLoss = false
                pausePlayback(withFade = false)
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                shouldResumeAfterTransientLoss = player.isPlaying
                pausePlayback(withFade = false)
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (shouldResumeAfterTransientLoss) {
                    shouldResumeAfterTransientLoss = false
                    resumePlayback()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        serviceScope = CoroutineScope(SupervisorJob() + applicationContext.appContainer.dispatchers.io)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        defaultArtwork = BitmapFactory.decodeResource(resources, R.drawable.default_album_identify)
        createNotificationChannel()
        configurePlayer()
        configureMediaSession()
        registerReceiver(screenStateReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
        progressHandler.post(progressTicker)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action?.requiresImmediateForegroundPromotion() == true) {
            ensureForegroundServiceStarted()
        }
        when (intent?.action) {
            ACTION_PLAY_FROM_QUEUE -> {
                val queueId = intent.getLongExtra(EXTRA_QUEUE_ID, -1L)
                val incomingQueue = PlaybackQueueStore.get(queueId).orEmpty()
                val incomingIndex = intent.getIntExtra(EXTRA_INDEX, 0)
                if (incomingQueue.isNotEmpty()) {
                    queue = incomingQueue
                    updateMediaSessionQueue()
                    playIndex(incomingIndex, playWhenReady = true)
                }
            }

            ACTION_ENQUEUE -> {
                val queueId = intent.getLongExtra(EXTRA_QUEUE_ID, -1L)
                val incomingQueue = PlaybackQueueStore.get(queueId).orEmpty()
                if (incomingQueue.isNotEmpty()) {
                    queue = queue + incomingQueue
                    if (currentIndex !in queue.indices) {
                        currentIndex = 0
                    }
                    updateMediaSessionQueue()
                    updateNotification()
                }
            }

            ACTION_PLAY_NEXT -> {
                val queueId = intent.getLongExtra(EXTRA_QUEUE_ID, -1L)
                val incomingQueue = PlaybackQueueStore.get(queueId).orEmpty()
                if (incomingQueue.isNotEmpty()) {
                    if (queue.isEmpty()) {
                        queue = incomingQueue
                        updateMediaSessionQueue()
                        playIndex(0, playWhenReady = true)
                    } else {
                        val insertIndex = (currentIndex + 1).coerceIn(0, queue.size)
                        queue = buildList(queue.size + incomingQueue.size) {
                            addAll(queue.subList(0, insertIndex))
                            addAll(incomingQueue)
                            addAll(queue.subList(insertIndex, queue.size))
                        }
                        updateMediaSessionQueue()
                    }
                }
            }

            ACTION_REPLACE_QUEUE -> {
                val queueId = intent.getLongExtra(EXTRA_QUEUE_ID, -1L)
                val incomingQueue = PlaybackQueueStore.get(queueId).orEmpty()
                val incomingIndex = intent.getIntExtra(EXTRA_INDEX, 0)
                replaceQueue(incomingQueue, incomingIndex)
            }

            ACTION_TOGGLE_PLAY_PAUSE -> {
                if (player.isPlaying) pausePlayback() else resumePlayback()
            }

            ACTION_NEXT -> playNext()
            ACTION_PREVIOUS -> playPrevious()
            ACTION_SEEK_TO -> seekTo(intent.getIntExtra(EXTRA_SEEK_POSITION_MS, 0))
            ACTION_SET_STOP_AFTER_CURRENT_TRACK -> {
                stopAfterCurrentTrack = intent.getBooleanExtra(EXTRA_STOP_AFTER_CURRENT_TRACK, false)
            }

            ACTION_APPLY_AUDIO_EFFECTS -> {
                AudioEffectsManager.applyFromPreferences(this, player)
                applyResolvedPlayerVolume()
            }
            ACTION_APPLY_PLAYBACK_TUNING -> applyPlaybackTuning()
            ACTION_PLAY -> resumePlayback()
            ACTION_PAUSE -> pausePlayback()
            ACTION_RESTART_CURRENT -> restartCurrentTrack()
            ACTION_REFRESH_NOTIFICATION_STYLE -> updateNotification()
            ACTION_CLEAR_QUEUE -> stopPlayback()
            ACTION_STOP -> stopPlayback()
        }
        if (!player.isPlaying && currentIndex !in queue.indices && isForegroundStarted) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            notificationManager.cancel(NOTIFICATION_ID)
            isForegroundStarted = false
        }
        publishState()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        progressHandler.removeCallbacksAndMessages(null)
        fadeAnimationId += 1L
        artworkTarget?.let { Glide.with(this).clear(it) }
        mediaSession.release()
        AudioEffectsManager.release()
        player.release()
        abandonAudioFocus()
        serviceScope.cancel()
        unregisterReceiver(screenStateReceiver)
        MusicPlaybackController.reset()
        super.onDestroy()
    }

    private fun configurePlayer() {
        player = ExoPlayer.Builder(this)
            .setHandleAudioBecomingNoisy(true)
            .setAudioAttributes(
                androidx.media3.common.AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                false
            )
            .build()
        applyPlaybackTuning()

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> {
                        AudioEffectsManager.attachAndApply(this@MusicPlayService, player)
                        applyResolvedPlayerVolume()
                    }
                    Player.STATE_ENDED -> {
                        timedTransitionTrackId = Long.MIN_VALUE
                        timedTransitionStartedAtMs = 0L
                        recordCurrentTrackCompletion()
                        if (stopAfterCurrentTrack) {
                            stopAfterCurrentTrack = false
                            stopPlayback()
                            return
                        }
                        playNext(fromAutoTransition = true)
                        return
                    }
                }
                updateNotification()
                updatePlaybackStateForMediaSession()
                publishState()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    AudioEffectsManager.applyFromPreferences(this@MusicPlayService, player)
                    applyResolvedPlayerVolume()
                    recordCurrentTrackStartIfNeeded()
                }
                updateNotification()
                updatePlaybackStateForMediaSession()
                publishState()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                timedTransitionTrackId = Long.MIN_VALUE
                timedTransitionStartedAtMs = 0L
                applyResolvedPlayerVolume()
                refreshArtworkAndSession()
                if (player.isPlaying) {
                    recordCurrentTrackStartIfNeeded(force = true)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                playNext()
            }
        })
    }

    private fun configureMediaSession() {
        val mediaButtonIntent = Intent(this, MediaButtonReceiver::class.java).apply {
            action = Intent.ACTION_MEDIA_BUTTON
        }
        val pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val mediaButtonPendingIntent =
            PendingIntent.getBroadcast(this, 0, mediaButtonIntent, pendingIntentFlags)
        val sessionActivity = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_EXPAND_PLAYER, true)
            },
            pendingIntentFlags
        )

        mediaSession = MediaSession(this, MEDIA_SESSION_TAG).apply {
            setMediaButtonReceiver(mediaButtonPendingIntent)
            setSessionActivity(sessionActivity)
            isActive = true
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = resumePlayback()
                override fun onPause() = pausePlayback()
                override fun onSkipToNext() = playNext()
                override fun onSkipToPrevious() = playPrevious()
                override fun onSeekTo(pos: Long) = seekTo(pos.toInt())

                override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                    val event: KeyEvent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
                    }
                    if (event?.action != KeyEvent.ACTION_DOWN) return true
                    HeadsetMediaButtonHandler.handle(this@MusicPlayService, event.keyCode)
                    return true
                }
            })
        }
        updateMediaSessionQueue()
        updatePlaybackStateForMediaSession()
    }

    private fun playIndex(index: Int, playWhenReady: Boolean) {
        if (queue.isEmpty()) return
        currentIndex = index.coerceIn(0, queue.lastIndex)
        lastStartedTrackId = Long.MIN_VALUE
        timedTransitionTrackId = Long.MIN_VALUE
        timedTransitionStartedAtMs = 0L
        val track = queue[currentIndex]
        val source = track.data.orEmpty()
        if (source.isBlank()) {
            playNext()
            return
        }

        if (playWhenReady && !requestAudioFocus()) return

        player.stop()
        player.clearMediaItems()
        val mediaUri = if (source.contains("://")) Uri.parse(source) else Uri.fromFile(File(source))
        player.setMediaItem(MediaItem.fromUri(mediaUri))
        player.prepare()
        player.playWhenReady = playWhenReady
        if (playWhenReady && isPlayPauseFadeEnabled()) {
            fadePlayerVolume(
                from = 0f,
                to = resolveTargetPlaybackVolume(),
                durationMs = PLAY_PAUSE_FADE_DURATION_MS
            )
        } else {
            applyResolvedPlayerVolume()
        }
        refreshArtworkAndSession(force = true)
        updatePlaybackStateForMediaSession()
    }

    private fun resumePlayback() {
        if (queue.isEmpty()) return
        if (!requestAudioFocus()) return
        if (currentIndex !in queue.indices) {
            playIndex(0, playWhenReady = true)
            return
        }
        if (!player.isPlaying) {
            if (player.playbackState == Player.STATE_IDLE) {
                playIndex(currentIndex, playWhenReady = true)
                return
            }
            player.play()
            applyPlaybackTuning()
            AudioEffectsManager.applyFromPreferences(this, player)
            if (isPlayPauseFadeEnabled()) {
                fadePlayerVolume(
                    from = 0f,
                    to = resolveTargetPlaybackVolume(),
                    durationMs = PLAY_PAUSE_FADE_DURATION_MS
                )
            }
            updateNotification()
            updatePlaybackStateForMediaSession()
            publishState()
        }
    }

    private fun pausePlayback(withFade: Boolean = isPlayPauseFadeEnabled()) {
        if (player.isPlaying) {
            if (withFade) {
                val targetVolume = player.volume
                fadePlayerVolume(
                    from = targetVolume,
                    to = 0f,
                    durationMs = PLAY_PAUSE_FADE_DURATION_MS
                ) {
                    player.pause()
                    applyResolvedPlayerVolume()
                    updateNotification()
                    updatePlaybackStateForMediaSession()
                    publishState()
                }
            } else {
                player.pause()
                updateNotification()
                updatePlaybackStateForMediaSession()
                publishState()
            }
        }
    }

    private fun restartCurrentTrack() {
        if (currentIndex !in queue.indices) return
        timedTransitionTrackId = Long.MIN_VALUE
        timedTransitionStartedAtMs = 0L
        player.seekTo(0)
        if (!player.isPlaying) {
            resumePlayback()
        } else {
            publishState()
        }
    }

    private fun stopPlayback() {
        artworkTarget?.let { Glide.with(this).clear(it) }
        artworkTarget = null
        currentArtwork = null
        currentArtworkTrackId = Long.MIN_VALUE
        lastStartedTrackId = Long.MIN_VALUE
        timedTransitionTrackId = Long.MIN_VALUE
        timedTransitionStartedAtMs = 0L
        fadeAnimationId += 1L
        player.stop()
        player.clearMediaItems()
        queue = emptyList()
        currentIndex = -1
        mediaSession.setMetadata(null)
        mediaSession.setQueue(emptyList())
        abandonAudioFocus()
        stopForeground(STOP_FOREGROUND_REMOVE)
        notificationManager.cancel(NOTIFICATION_ID)
        isForegroundStarted = false
        MusicPlaybackController.reset()
        stopSelf()
    }

    private fun replaceQueue(newQueue: List<Music>, requestedIndex: Int) {
        if (newQueue.isEmpty()) {
            stopPlayback()
            return
        }

        val previousTrackId = queue.getOrNull(currentIndex)?._id
        val previousPosition = player.currentPosition
        val wasPlaying = player.isPlaying

        queue = newQueue
        currentIndex = requestedIndex.coerceIn(0, newQueue.lastIndex)
        timedTransitionTrackId = Long.MIN_VALUE
        timedTransitionStartedAtMs = 0L
        updateMediaSessionQueue()

        val selectedTrack = queue.getOrNull(currentIndex)
        val canKeepCurrentTrack =
            previousTrackId != null &&
                selectedTrack?._id == previousTrackId &&
                player.currentMediaItem != null &&
                player.playbackState != Player.STATE_IDLE

        if (canKeepCurrentTrack) {
            player.seekTo(previousPosition)
            refreshArtworkAndSession(force = false)
            updateNotification()
            updatePlaybackStateForMediaSession()
            publishState()
            return
        }

        playIndex(currentIndex, playWhenReady = wasPlaying)
    }

    private fun playNext(fromAutoTransition: Boolean = false) {
        if (queue.isEmpty()) return
        timedTransitionTrackId = Long.MIN_VALUE
        timedTransitionStartedAtMs = 0L
        val nextIndex = resolveNextIndex(fromAutoTransition) ?: run {
            if (fromAutoTransition) {
                stopPlayback()
            }
            return
        }
        playIndex(nextIndex, playWhenReady = true)
    }

    private fun playPrevious() {
        if (queue.isEmpty()) return
        if (player.currentPosition > PREVIOUS_RESTART_WINDOW_MS) {
            seekTo(0)
            return
        }
        val previousIndex = resolvePreviousIndex() ?: return
        playIndex(previousIndex, playWhenReady = true)
    }

    private fun resolveNextIndex(fromAutoTransition: Boolean): Int? {
        val mode = PreferenceUtil.getInstance(this).getPlayMode()
        val activeIndex = currentIndex.takeIf { it in queue.indices } ?: return 0
        return when (mode) {
            PlaybackMode.SHUFFLE_ALL -> randomOtherIndex(activeIndex) ?: activeIndex
            PlaybackMode.LOOP_ALL -> (activeIndex + 1) % queue.size
            else -> {
                if (activeIndex == queue.lastIndex) {
                    null
                } else {
                    activeIndex + 1
                }
            }
        }
    }

    private fun resolvePreviousIndex(): Int? {
        val mode = PreferenceUtil.getInstance(this).getPlayMode()
        val activeIndex = currentIndex.takeIf { it in queue.indices } ?: return 0
        return when (mode) {
            PlaybackMode.SHUFFLE_ALL -> randomOtherIndex(activeIndex) ?: activeIndex
            PlaybackMode.LOOP_ALL -> if (activeIndex == 0) queue.lastIndex else activeIndex - 1
            else -> if (activeIndex == 0) 0 else activeIndex - 1
        }
    }

    private fun randomOtherIndex(current: Int): Int? {
        if (queue.size <= 1) return null
        val candidates = queue.indices.filterNot { it == current }
        return candidates.random(Random(System.nanoTime()))
    }

    private fun seekTo(positionMs: Int) {
        if (currentIndex !in queue.indices) return
        val durationMs = player.duration.takeIf { it != C.TIME_UNSET }?.coerceAtLeast(0L)?.toInt() ?: 0
        val target = positionMs.coerceIn(0, durationMs)
        player.seekTo(target.toLong())
        publishState()
        updatePlaybackStateForMediaSession()
    }

    private fun refreshArtworkAndSession(force: Boolean = false) {
        val track = queue.getOrNull(currentIndex)
        if (track == null) {
            currentArtwork = null
            currentArtworkTrackId = Long.MIN_VALUE
            mediaSession.setMetadata(null)
            updateNotification()
            return
        }

        if (!force && currentArtworkTrackId == track._id) {
            updateMediaSessionMetadata(track, currentArtwork ?: defaultArtwork)
            updateNotification()
            return
        }

        artworkTarget?.let { Glide.with(this).clear(it) }
        val target = object : CustomTarget<Bitmap>(NOTIFICATION_ART_SIZE_PX, NOTIFICATION_ART_SIZE_PX) {
            override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                currentArtwork = resource
                currentArtworkTrackId = track._id
                updateMediaSessionMetadata(track, resource)
                updateNotification()
            }

            override fun onLoadFailed(errorDrawable: android.graphics.drawable.Drawable?) {
                currentArtwork = defaultArtwork
                currentArtworkTrackId = track._id
                updateMediaSessionMetadata(track, defaultArtwork)
                updateNotification()
            }

            override fun onLoadCleared(placeholder: android.graphics.drawable.Drawable?) {
                if (currentArtworkTrackId == track._id) {
                    currentArtwork = defaultArtwork
                }
            }
        }
        artworkTarget = target

        Glide.with(this)
            .asBitmap()
            .load(track.albumArtSource())
            .centerCrop()
            .into(target)
    }

    private fun updateMediaSessionMetadata(track: Music, artwork: Bitmap) {
        mediaSession.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, track.title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, track.artist)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, track.album)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, track.duration.toLong())
                .putLong(MediaMetadata.METADATA_KEY_TRACK_NUMBER, (currentIndex + 1).toLong())
                .putLong(MediaMetadata.METADATA_KEY_NUM_TRACKS, queue.size.toLong())
                .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, artwork)
                .putBitmap(MediaMetadata.METADATA_KEY_ART, artwork)
                .build()
        )
    }

    private fun updateMediaSessionQueue() {
        val sessionQueue = queue.mapIndexed { index, track ->
            MediaSession.QueueItem(
                MediaDescription.Builder()
                    .setMediaId(track._id.toString())
                    .setTitle(track.title)
                    .setSubtitle(track.artist)
                    .setDescription(track.album)
                    .build(),
                index.toLong()
            )
        }
        mediaSession.setQueue(sessionQueue)
        mediaSession.setQueueTitle(getString(R.string.music_player))
    }

    private fun publishState() {
        val duration = runCatching {
            player.duration.takeIf { it != C.TIME_UNSET }?.coerceAtLeast(0L)?.toInt() ?: 0
        }.getOrDefault(0)
        val position = runCatching {
            player.currentPosition.coerceIn(0L, duration.toLong()).toInt()
        }.getOrDefault(0)
        MusicPlaybackController.publishState(
            MusicPlaybackState(
                queue = queue,
                currentIndex = currentIndex,
                isPlaying = player.isPlaying,
                positionMs = position,
                durationMs = duration,
                audioSessionId = player.audioSessionId.takeIf { it > 0 } ?: -1
            )
        )
        WidgetRenderer.updateAll(this)
    }

    private fun updatePlaybackStateForMediaSession() {
        val trackAvailable = currentIndex in queue.indices
        val state = when {
            !trackAvailable -> PlaybackState.STATE_STOPPED
            player.isPlaying -> PlaybackState.STATE_PLAYING
            else -> PlaybackState.STATE_PAUSED
        }
        val position = runCatching { player.currentPosition }.getOrDefault(0L)
        val speed = if (player.isPlaying) 1f else 0f
        mediaSession.setPlaybackState(
            PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or
                        PlaybackState.ACTION_PAUSE or
                        PlaybackState.ACTION_PLAY_PAUSE or
                        PlaybackState.ACTION_SKIP_TO_NEXT or
                        PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackState.ACTION_SEEK_TO or
                        PlaybackState.ACTION_STOP
                )
                .setState(state, position, speed)
                .build()
        )
    }

    private fun buildNotification(): Notification {
        val track = queue.getOrNull(currentIndex)
        val title = track?.title ?: getString(R.string.music_player)
        val text = track?.artist ?: getString(android.R.string.unknownName)
        val subText = track?.album
        val contentIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val contentIntent = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_EXPAND_PLAYER, true)
            },
            contentIntentFlags
        )

        val previousAction = Action.Builder(
            android.R.drawable.ic_media_previous,
            getString(R.string.operation_previous),
            serviceActionPendingIntent(ACTION_PREVIOUS, REQUEST_PREVIOUS)
        ).build()
        val playPauseAction = Action.Builder(
            if (player.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
            getString(if (player.isPlaying) R.string.operation_pause else R.string.operation_play),
            serviceActionPendingIntent(ACTION_TOGGLE_PLAY_PAUSE, REQUEST_TOGGLE_PLAY_PAUSE)
        ).build()
        val nextAction = Action.Builder(
            android.R.drawable.ic_media_next,
            getString(R.string.operation_next),
            serviceActionPendingIntent(ACTION_NEXT, REQUEST_NEXT)
        ).build()
        val stopAction = Action.Builder(
            android.R.drawable.ic_menu_close_clear_cancel,
            getString(R.string.operation_stop),
            serviceActionPendingIntent(ACTION_STOP, REQUEST_STOP)
        ).build()

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }
        val preferences = PreferenceUtil.getInstance(this)
        val useOldNotification = preferences.getBooleanPreference("old_notification", false)
        val useColorNotification = preferences.getBooleanPreference("color_notification", true)

        builder
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setSubText(subText)
            .setContentIntent(contentIntent)
            .setDeleteIntent(serviceActionPendingIntent(ACTION_STOP, REQUEST_DELETE))
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setOngoing(player.isPlaying)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setLargeIcon(currentArtwork ?: defaultArtwork)
            .addAction(previousAction)
            .addAction(playPauseAction)
            .addAction(nextAction)
            .addAction(stopAction)
            .setStyle(
                MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setColorized(!useOldNotification && useColorNotification)
        }

        return builder.build()
    }

    private fun ensureForegroundServiceStarted() {
        if (isForegroundStarted) return
        val notification = buildNotification()
        if (tryStartForeground(notification)) {
            isForegroundStarted = true
        } else {
            notificationManager.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun serviceActionPendingIntent(action: String, requestCode: Int): PendingIntent {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val intent = Intent(this, MusicPlayService::class.java).setAction(action)
        return PendingIntent.getService(this, requestCode, intent, flags)
    }

    private fun updateNotification() {
        if (currentIndex !in queue.indices) return
        val notification = buildNotification()
        if (player.isPlaying) {
            if (!isForegroundStarted) {
                if (tryStartForeground(notification)) {
                    isForegroundStarted = true
                } else {
                    notificationManager.notify(NOTIFICATION_ID, notification)
                }
            } else {
                notificationManager.notify(NOTIFICATION_ID, notification)
            }
        } else {
            if (isForegroundStarted) {
                stopForeground(STOP_FOREGROUND_DETACH)
                isForegroundStarted = false
            }
            notificationManager.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun tryStartForeground(notification: Notification): Boolean {
        return try {
            startForeground(NOTIFICATION_ID, notification)
            true
        } catch (error: Throwable) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                error is ForegroundServiceStartNotAllowedException
            ) {
                false
            } else {
                throw error
            }
        }
    }

    private fun requestAudioFocus(): Boolean {
        if (PreferenceUtil.getInstance(this).getBooleanPreference(KEY_SIMULTANEOUS_PLAY, false)) {
            hasAudioFocus = false
            return true
        }
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val focusResult = audioManager.requestAudioFocus(
            audioFocusChangeListener,
            AudioManager.STREAM_MUSIC,
            AudioManager.AUDIOFOCUS_GAIN
        )
        hasAudioFocus = focusResult == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return hasAudioFocus
    }

    private fun applyPlaybackTuning() {
        val preferences = PreferenceUtil.getInstance(this)
        val speed = preferences.getPlaySpeed().coerceIn(0.5f, 2.0f)
        val pitch = preferences.getPlayPitch().coerceIn(0.5f, 2.0f)
        player.playbackParameters = PlaybackParameters(speed, pitch)
        applyResolvedPlayerVolume()
    }

    private fun applyResolvedPlayerVolume() {
        player.volume = resolveTargetPlaybackVolume()
    }

    private fun isPlayPauseFadeEnabled(): Boolean =
        PreferenceUtil.getInstance(this).getBooleanPreference(KEY_VOLUME_FADE, false)

    private fun resolveTargetPlaybackVolume(): Float {
        val masterVolume = AudioEffectsManager.loadSettings(this).masterVolume.coerceIn(0f, 1f)
        val replayGainMultiplier = ReplayGainPreferences.resolveVolumeMultiplier(
            context = this,
            info = ReplayGainParser.parse(queue.getOrNull(currentIndex)?.data)
        )
        return (masterVolume * replayGainMultiplier).coerceIn(0f, 4f)
    }

    private fun fadePlayerVolume(
        from: Float,
        to: Float,
        durationMs: Long,
        onEnd: (() -> Unit)? = null
    ) {
        val animationId = ++fadeAnimationId
        val startAt = SystemClock.elapsedRealtime()
        val safeDurationMs = durationMs.coerceAtLeast(1L)

        fun step() {
            if (animationId != fadeAnimationId) return
            val elapsed = (SystemClock.elapsedRealtime() - startAt).coerceAtMost(safeDurationMs)
            val progress = elapsed.toFloat() / safeDurationMs.toFloat()
            player.volume = from + ((to - from) * progress)
            if (elapsed >= safeDurationMs) {
                player.volume = to
                onEnd?.invoke()
                return
            }
            progressHandler.postDelayed(::step, FADE_STEP_MS)
        }

        step()
    }

    private fun maybeHandleTimedTransition() {
        if (!player.isPlaying || currentIndex !in queue.indices) return
        val durationMs = player.duration.takeIf { it != C.TIME_UNSET } ?: return
        val remainingMs = durationMs - player.currentPosition
        val currentTrackId = queue[currentIndex]._id
        if (timedTransitionTrackId == currentTrackId) return

        val preferences = PreferenceUtil.getInstance(this)
        if (preferences.getBooleanPreference(KEY_CROSS_FADE, false)) {
            val fadeDurationMs = preferences.getIntPreference(KEY_FADE_DURATION_MS, 6000).coerceIn(1_000, 12_000)
            if (remainingMs in 1..fadeDurationMs.toLong() && resolveNextIndex(fromAutoTransition = true) != null) {
                timedTransitionTrackId = currentTrackId
                timedTransitionStartedAtMs = SystemClock.elapsedRealtime()
                fadePlayerVolume(player.volume, 0f, fadeDurationMs.toLong()) {
                    if (queue.getOrNull(currentIndex)?._id == currentTrackId) {
                        playNext(fromAutoTransition = true)
                    }
                }
            }
            return
        }

        if (preferences.getBooleanPreference(KEY_GAPLESS_PLAYBACK, false) &&
            remainingMs in 1..GAPLESS_ADVANCE_WINDOW_MS &&
            resolveNextIndex(fromAutoTransition = true) != null
        ) {
            timedTransitionTrackId = currentTrackId
            timedTransitionStartedAtMs = SystemClock.elapsedRealtime()
            playNext(fromAutoTransition = true)
        }
    }

    private fun abandonAudioFocus() {
        if (!hasAudioFocus) return
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.abandonAudioFocus(audioFocusChangeListener)
        hasAudioFocus = false
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.music_player),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            setShowBadge(false)
            description = getString(R.string.music_player)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun recordCurrentTrackStartIfNeeded(force: Boolean = false) {
        val track = queue.getOrNull(currentIndex) ?: return
        if (!force && lastStartedTrackId == track._id) return
        lastStartedTrackId = track._id
        serviceScope.launch {
            applicationContext.appContainer.musicDao.updateTrackPlayTime(
                trackId = track._id,
                playTime = System.currentTimeMillis()
            )
        }
    }

    private fun recordCurrentTrackCompletion() {
        val track = queue.getOrNull(currentIndex) ?: return
        serviceScope.launch {
            applicationContext.appContainer.musicDao.incrementTrackPlayCount(track._id)
        }
    }

    private fun String.requiresImmediateForegroundPromotion(): Boolean {
        return this == ACTION_PLAY_FROM_QUEUE ||
            this == ACTION_PLAY ||
            this == ACTION_PLAY_NEXT ||
            this == ACTION_REPLACE_QUEUE ||
            this == ACTION_TOGGLE_PLAY_PAUSE ||
            this == ACTION_NEXT ||
            this == ACTION_PREVIOUS
    }

    companion object {
        const val ACTION_PLAY_FROM_QUEUE = "gd.app.musicplayer.action.PLAY_FROM_QUEUE"
        const val ACTION_ENQUEUE = "gd.app.musicplayer.action.ENQUEUE"
        const val ACTION_PLAY_NEXT = "gd.app.musicplayer.action.PLAY_NEXT"
        const val ACTION_REPLACE_QUEUE = "gd.app.musicplayer.action.REPLACE_QUEUE"
        const val ACTION_TOGGLE_PLAY_PAUSE = "gd.app.musicplayer.action.TOGGLE_PLAY_PAUSE"
        const val ACTION_PLAY = "gd.app.musicplayer.action.PLAY"
        const val ACTION_PAUSE = "gd.app.musicplayer.action.PAUSE"
        const val ACTION_NEXT = "gd.app.musicplayer.action.NEXT"
        const val ACTION_PREVIOUS = "gd.app.musicplayer.action.PREVIOUS"
        const val ACTION_SEEK_TO = "gd.app.musicplayer.action.SEEK_TO"
        const val ACTION_CLEAR_QUEUE = "gd.app.musicplayer.action.CLEAR_QUEUE"
        const val ACTION_STOP = "gd.app.musicplayer.action.STOP"
        const val ACTION_SET_STOP_AFTER_CURRENT_TRACK =
            "gd.app.musicplayer.action.SET_STOP_AFTER_CURRENT_TRACK"
        const val ACTION_APPLY_AUDIO_EFFECTS =
            "gd.app.musicplayer.action.APPLY_AUDIO_EFFECTS"
        const val ACTION_APPLY_PLAYBACK_TUNING =
            "gd.app.musicplayer.action.APPLY_PLAYBACK_TUNING"
        const val ACTION_REFRESH_NOTIFICATION_STYLE =
            "gd.app.musicplayer.action.REFRESH_NOTIFICATION_STYLE"
        const val ACTION_RESTART_CURRENT =
            "gd.app.musicplayer.action.RESTART_CURRENT"

        const val EXTRA_QUEUE_ID = "queue_id"
        const val EXTRA_INDEX = "index"
        const val EXTRA_SEEK_POSITION_MS = "seek_position_ms"
        const val EXTRA_STOP_AFTER_CURRENT_TRACK = "stop_after_current_track"

        private const val NOTIFICATION_CHANNEL_ID = "music_playback"
        private const val NOTIFICATION_ID = 1001
        private const val PROGRESS_TICK_MS = 500L
        private const val MEDIA_SESSION_TAG = "MusicPlayServiceSession"
        private const val PREVIOUS_RESTART_WINDOW_MS = 5_000L
        private const val NOTIFICATION_ART_SIZE_PX = 512
        private const val REQUEST_PREVIOUS = 21
        private const val REQUEST_TOGGLE_PLAY_PAUSE = 22
        private const val REQUEST_NEXT = 23
        private const val REQUEST_STOP = 24
        private const val REQUEST_DELETE = 25
        private const val PLAY_PAUSE_FADE_DURATION_MS = 1_000L
        private const val FADE_STEP_MS = 50L
        private const val GAPLESS_ADVANCE_WINDOW_MS = 150L
        private const val KEY_SIMULTANEOUS_PLAY = "simultaneous_play"
        private const val KEY_VOLUME_FADE = "preference_volume_fade"
        private const val KEY_CROSS_FADE = "fade_enable"
        private const val KEY_GAPLESS_PLAYBACK = "gapless_play"
        private const val KEY_FADE_DURATION_MS = "fade_duration"
    }
}
