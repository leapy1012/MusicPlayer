package gd.app.musicplayer.playback

import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.os.Build
import androidx.palette.graphics.Palette
import gd.app.musicplayer.data.local.preference.NotificationSettingPreference
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.playback.notification.BaseMusicNotificationBuilder
import gd.app.musicplayer.playback.notification.DefaultMusicNotificationContent
import gd.app.musicplayer.playback.notification.NotificationAlbumArtwork
import gd.app.musicplayer.playback.service.MusicPlaybackService
import gd.app.musicplayer.ui.shell.MainActivity

class PlaybackNotificationController(
    private val service: Service,
    private val notificationManager: NotificationManager,
    private val mediaSessionTokenProvider: () -> android.media.session.MediaSession.Token,
    private val currentMusicProvider: () -> Music?,
    private val currentArtworkProvider: () -> Bitmap?,
    private val artworkTrackIdProvider: () -> Long,
    private val isEffectivelyPlaying: () -> Boolean,
    private val isFavoriteProvider: () -> Boolean,
    private val desktopLyricsEnabledProvider: () -> Boolean,
    private val notificationSettingsProvider: () -> NotificationSettingPreference,
) {

    private var notificationBuilder: BaseMusicNotificationBuilder? = null
    private var foregroundStarted = false
    private var lastRenderState: NotificationRenderState? = null

    private var cachedPaletteTrackId: Long = NO_TRACK_ID
    private var cachedPalette: Palette? = null

    val isForegroundStarted: Boolean
        get() = foregroundStarted

    fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            BaseMusicNotificationBuilder.CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            setShowBadge(false)
            description = BaseMusicNotificationBuilder.CHANNEL_NAME
        }

        notificationManager.createNotificationChannel(channel)
    }

    /**
     * Start foreground only when needed, but keep the same notification ID.
     */
    fun ensureForegroundStarted() {
        val notification = buildNotification()

        if (foregroundStarted) {
            notificationManager.notify(NOTIFICATION_ID, notification)
            return
        }

        if (tryStartForeground(notification)) {
            foregroundStarted = true
        } else {
            notificationManager.notify(NOTIFICATION_ID, notification)
        }
    }

    /**
     * Update the existing notification contents.
     *
     * Important:
     * - Do not cancel before notify.
     * - Do not remove notification just because player is paused.
     * - Same NOTIFICATION_ID means Android updates the current notification.
     */
    fun update(
        force: Boolean = false,
        keepWhenPaused: Boolean = true
    ) {
        val renderState = createRenderState()

        if (!force && renderState == lastRenderState) {
            return
        }

        val notification = buildNotification()
        lastRenderState = renderState

        val hasTrack = currentMusicProvider() != null
        val shouldBeForeground = isEffectivelyPlaying() || keepWhenPaused

        if (shouldBeForeground && hasTrack) {
            if (!foregroundStarted) {
                if (tryStartForeground(notification)) {
                    foregroundStarted = true
                } else {
                    notificationManager.notify(NOTIFICATION_ID, notification)
                }
            } else {
                notificationManager.notify(NOTIFICATION_ID, notification)
            }
            return
        }

        if (foregroundStarted) {
            service.stopForeground(Service.STOP_FOREGROUND_DETACH)
            foregroundStarted = false
        }

        if (hasTrack) {
            notificationManager.notify(NOTIFICATION_ID, notification)
        } else {
            notificationManager.cancel(NOTIFICATION_ID)
        }
    }

    /**
     * Refresh style without canceling the visible notification.
     * Canceling causes the "notification recreated" visual effect.
     */
    fun refreshStyle(postDelayed: (delayMs: Long, block: () -> Unit) -> Unit) {
        notificationBuilder?.cancel()
        notificationBuilder = null

        lastRenderState = null
        cachedPaletteTrackId = NO_TRACK_ID
        cachedPalette = null

        update(force = true)

        postDelayed(NOTIFICATION_STYLE_REFRESH_DELAY_MS) {
            update(force = true)
        }
    }

    fun stopForegroundAndRemove() {
        runCatching {
            service.stopForeground(Service.STOP_FOREGROUND_REMOVE)
        }

        notificationManager.cancel(NOTIFICATION_ID)
        foregroundStarted = false
        lastRenderState = null
    }

    fun stopForegroundDetached() {
        if (!foregroundStarted) return

        runCatching {
            service.stopForeground(Service.STOP_FOREGROUND_DETACH)
        }

        foregroundStarted = false
    }

    fun stopForegroundIfIdle(
        hasQueueItem: Boolean,
        keepIdleNotification: Boolean
    ) {
        if (
            !isEffectivelyPlaying() &&
            !hasQueueItem &&
            foregroundStarted &&
            !keepIdleNotification
        ) {
            stopForegroundAndRemove()
        }
    }

    fun cancelNotification() {
        notificationManager.cancel(NOTIFICATION_ID)
        lastRenderState = null
    }

    fun markForegroundStopped() {
        foregroundStarted = false
    }

    private fun buildNotification(): Notification {
        val music = currentMusicProvider()
        val artwork = currentArtworkProvider()
        val artworkTrackId = artworkTrackIdProvider()

        val contentIntent = PendingIntent.getActivity(
            service,
            CONTENT_REQUEST_CODE,
            Intent(service, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_EXPAND_PLAYER, true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = notificationBuilder ?: BaseMusicNotificationBuilder.create(
            context = service,
            shouldUseDynamicColors = true,
            notificationSettings = notificationSettingsProvider()
        ).also { createdBuilder ->
            notificationBuilder = createdBuilder
        }

        val palette = resolvePalette(
            artwork = artwork,
            artworkTrackId = artworkTrackId
        )

        val content = DefaultMusicNotificationContent(
            music = music,
            playing = isEffectivelyPlaying(),
            desktopLyricsEnabled = desktopLyricsEnabledProvider(),
            albumArt = NotificationAlbumArtwork(
                originalBitmap = artwork,
                displayBitmap = artwork,
                palette = palette
            ),
            contentIntent = contentIntent,
            actionIntentFactory = ::serviceActionPendingIntent,
            mediaSessionToken = mediaSessionTokenProvider()
        )

        return builder.buildNotification(content)
    }

    private fun resolvePalette(
        artwork: Bitmap?,
        artworkTrackId: Long
    ): Palette? {
        if (artwork == null) {
            cachedPaletteTrackId = NO_TRACK_ID
            cachedPalette = null
            return null
        }

        if (
            cachedPaletteTrackId == artworkTrackId &&
            cachedPalette != null
        ) {
            return cachedPalette
        }

        val palette = runCatching {
            Palette.from(artwork).generate()
        }.getOrNull()

        cachedPaletteTrackId = artworkTrackId
        cachedPalette = palette

        return palette
    }

    private fun serviceActionPendingIntent(
        action: String,
        requestCode: Int
    ): PendingIntent {
        val intent = Intent(service, MusicPlaybackService::class.java)
            .setAction(action)

        return PendingIntent.getService(
            service,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createRenderState(): NotificationRenderState {
        val music = currentMusicProvider()

        return NotificationRenderState(
            trackId = music?.id ?: NO_TRACK_ID,
            favorite = isFavoriteProvider(),
            playing = isEffectivelyPlaying(),
            artworkTrackId = artworkTrackIdProvider(),
            artworkReady = currentArtworkProvider() != null,
            desktopLyricsEnabled = desktopLyricsEnabledProvider()
        )
    }

    private fun tryStartForeground(notification: Notification): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                service.startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                service.startForeground(
                    NOTIFICATION_ID,
                    notification
                )
            }

            true
        } catch (error: Throwable) {
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                error is ForegroundServiceStartNotAllowedException
            ) {
                false
            } else {
                throw error
            }
        }
    }

    companion object {
        private const val NOTIFICATION_CHANNEL_ID =
            BaseMusicNotificationBuilder.CHANNEL_ID

        private const val NOTIFICATION_ID =
            BaseMusicNotificationBuilder.NOTIFICATION_ID

        private const val CONTENT_REQUEST_CODE = 1
        private const val NOTIFICATION_STYLE_REFRESH_DELAY_MS = 1_500L
        private const val NO_TRACK_ID = Long.MIN_VALUE
    }
}