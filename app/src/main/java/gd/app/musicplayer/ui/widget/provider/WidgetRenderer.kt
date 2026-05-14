package gd.app.musicplayer.ui.widget.provider

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.isFavorite
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.playback.service.MusicPlaybackService
import gd.app.musicplayer.playback.PlaybackMode
import gd.app.musicplayer.ui.widget.WidgetArtworkStyle
import gd.app.musicplayer.ui.widget.WidgetCatalog
import gd.app.musicplayer.ui.widget.WidgetConfig
import gd.app.musicplayer.ui.widget.WidgetConfigActivity
import gd.app.musicplayer.ui.widget.shouldUseDarkForeground
import gd.app.musicplayer.ui.widget.WidgetQueueService
import gd.app.musicplayer.ui.shell.MainActivity
import java.io.File
import java.io.InputStream
import kotlin.math.roundToInt

internal object WidgetRenderer {

    fun updateWidgets(
        context: Context,
        manager: AppWidgetManager,
        appWidgetIds: IntArray,
        classify: String,
        snapshot: WidgetPlaybackSnapshot,
        configs: Map<Int, WidgetConfig>
    ) {
        appWidgetIds.forEach { appWidgetId ->
            val remoteViews = buildRemoteViews(
                context = context,
                appWidgetId = appWidgetId,
                classify = classify,
                snapshot = snapshot,
                config = configs[appWidgetId] ?: defaultConfig(classify),
                allowBitmapArtwork = true
            )

            runCatching {
                manager.updateAppWidget(appWidgetId, remoteViews)
            }.recoverCatching { throwable ->
                if (throwable !is IllegalArgumentException ||
                    throwable.message?.contains("RemoteViews", ignoreCase = true) != true
                ) {
                    throw throwable
                }

                val fallbackRemoteViews = buildRemoteViews(
                    context = context,
                    appWidgetId = appWidgetId,
                    classify = classify,
                    snapshot = snapshot,
                    config = configs[appWidgetId] ?: defaultConfig(classify),
                    allowBitmapArtwork = false
                )
                manager.updateAppWidget(appWidgetId, fallbackRemoteViews)
            }.getOrThrow()

            if (classify == CLASSIFY_LIST) {
                manager.notifyAppWidgetViewDataChanged(
                    appWidgetId,
                    R.id.widget_queue
                )
            }
        }
    }

    private fun buildRemoteViews(
        context: Context,
        appWidgetId: Int,
        classify: String,
        snapshot: WidgetPlaybackSnapshot,
        config: WidgetConfig,
        allowBitmapArtwork: Boolean
    ): RemoteViews {
        val spec = WidgetCatalog.specForClassify(classify)

        val style = spec.styles.firstOrNull { style ->
            style.styleKey == config.styleKey
        } ?: spec.styles.first()

        val theme = WidgetCatalog.themeOption(
            themeType = config.themeType,
            themeIndex = config.themeIndex
        )

        val track = snapshot.currentTrack
        val remoteViews = RemoteViews(context.packageName, style.layoutRes)

        val artworkStyle = WidgetCatalog.artworkStyle(style.styleKey)
        val useDarkForeground = theme.shouldUseDarkForeground()
        val textColor = if (useDarkForeground) DARK_FOREGROUND_PRIMARY else Color.WHITE
        val secondaryTextColor = if (useDarkForeground) {
            DARK_FOREGROUND_SECONDARY
        } else {
            LIGHT_FOREGROUND_SECONDARY
        }
        val iconColor = textColor

        remoteViews.setImageViewResource(
            R.id.widget_background_image,
            theme.drawableRes
        )

        remoteViews.setInt(
            R.id.widget_background_image,
            "setAlpha",
            (config.alpha * 255f).toInt()
        )

        remoteViews.setTextViewText(
            R.id.widget_title,
            track?.title ?: context.getString(R.string.music)
        )

        remoteViews.setTextViewText(
            R.id.widget_artist,
            track?.artist?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.artist)
        )

        remoteViews.setTextViewText(
            R.id.widget_queue_info,
            formatQueueInfo(snapshot)
        )

        remoteViews.setTextColor(R.id.widget_title, textColor)
        remoteViews.setTextColor(R.id.widget_artist, secondaryTextColor)
        remoteViews.setTextColor(R.id.widget_queue_info, secondaryTextColor)

        remoteViews.setInt(
            R.id.widget_flipper_play_pause,
            "setDisplayedChild",
            if (snapshot.isPlaying) 1 else 0
        )

        remoteViews.setViewVisibility(
            R.id.widget_play,
            if (snapshot.isPlaying) View.GONE else View.VISIBLE
        )

        remoteViews.setViewVisibility(
            R.id.widget_pause,
            if (snapshot.isPlaying) View.VISIBLE else View.GONE
        )

        val isFavorite = track?.isFavorite() == true

        remoteViews.setViewVisibility(
            R.id.widget_favorite_selected,
            if (isFavorite) View.VISIBLE else View.GONE
        )

        remoteViews.setViewVisibility(
            R.id.widget_favorite_unselected,
            if (isFavorite) View.GONE else View.VISIBLE
        )

        remoteViews.setInt(
            R.id.widget_flipper_favorite,
            "setDisplayedChild",
            if (isFavorite) 1 else 0
        )

        remoteViews.setImageViewResource(
            R.id.widget_mode,
            modeIcon(snapshot.playMode)
        )

        tintControl(remoteViews, R.id.widget_previous, iconColor)
        tintControl(remoteViews, R.id.widget_next, iconColor)
        tintControl(remoteViews, R.id.widget_mode, iconColor)
        tintControl(remoteViews, R.id.widget_play, iconColor)
        tintControl(remoteViews, R.id.widget_pause, iconColor)
        tintControl(remoteViews, R.id.widget_setting, iconColor)
        tintControl(
            remoteViews,
            R.id.widget_favorite_selected,
            ContextCompat.getColor(context, R.color.color_theme)
        )
        tintControl(remoteViews, R.id.widget_favorite_unselected, iconColor)
        bindControlBackgrounds(
            remoteViews = remoteViews,
            useDarkForeground = useDarkForeground
        )

        bindProgress(
            remoteViews = remoteViews,
            track = track,
            positionMs = snapshot.positionMs,
            useDarkForeground = useDarkForeground
        )

        bindArtwork(
            context = context,
            remoteViews = remoteViews,
            track = track,
            artworkStyle = artworkStyle,
            targetSizePx = artworkTargetSizePx(context, classify),
            allowBitmapArtwork = allowBitmapArtwork
        )

        bindActions(
            context = context,
            remoteViews = remoteViews,
            appWidgetId = appWidgetId,
            classify = classify,
            snapshot = snapshot,
            providerClass = spec.providerClass
        )

        return remoteViews
    }

    private fun bindProgress(
        remoteViews: RemoteViews,
        track: Music?,
        positionMs: Long,
        useDarkForeground: Boolean
    ) {
        val progress = if (track != null && track.duration > 0) {
            (positionMs * 100 / track.duration.toLong())
                .coerceIn(0L, 100L)
                .toInt()
        } else {
            0
        }

        remoteViews.setProgressBar(R.id.widget_progress, 100, progress, false)
        remoteViews.setProgressBar(R.id.widget_progress_black, 100, progress, false)
        remoteViews.setInt(
            R.id.widget_progress_flipper,
            "setDisplayedChild",
            if (useDarkForeground) 1 else 0
        )

        remoteViews.setViewVisibility(
            R.id.widget_progress,
            if (useDarkForeground) View.GONE else View.VISIBLE
        )

        remoteViews.setViewVisibility(
            R.id.widget_progress_black,
            if (useDarkForeground) View.VISIBLE else View.GONE
        )
    }

    private fun bindArtwork(
        context: Context,
        remoteViews: RemoteViews,
        track: Music?,
        artworkStyle: WidgetArtworkStyle,
        targetSizePx: Int,
        allowBitmapArtwork: Boolean
    ) {
        val artwork = if (allowBitmapArtwork) {
            loadArtwork(
                context = context,
                track = track,
                artworkStyle = artworkStyle,
                targetSizePx = targetSizePx
            )
        } else {
            null
        }

        if (artwork != null) {
            remoteViews.setImageViewBitmap(R.id.widget_album_image, artwork)
        } else {
            remoteViews.setImageViewResource(
                R.id.widget_album_image,
                artworkStyle.placeholderRes
            )
        }
    }

    private fun bindControlBackgrounds(
        remoteViews: RemoteViews,
        useDarkForeground: Boolean
    ) {
        val buttonBackground = if (useDarkForeground) {
            R.drawable.widget_click_bg_btn_black
        } else {
            R.drawable.widget_click_bg_btn
        }
        val settingBackground = if (useDarkForeground) {
            R.drawable.widget_click_bg_setting_black
        } else {
            R.drawable.widget_click_bg_setting
        }

        remoteViews.setInt(
            R.id.widget_previous,
            "setBackgroundResource",
            buttonBackground
        )
        remoteViews.setInt(
            R.id.widget_next,
            "setBackgroundResource",
            buttonBackground
        )
        remoteViews.setInt(
            R.id.widget_mode,
            "setBackgroundResource",
            buttonBackground
        )
        remoteViews.setInt(
            R.id.widget_flipper_play_pause,
            "setBackgroundResource",
            buttonBackground
        )
        remoteViews.setInt(
            R.id.widget_flipper_favorite,
            "setBackgroundResource",
            buttonBackground
        )
        remoteViews.setInt(
            R.id.widget_setting,
            "setBackgroundResource",
            settingBackground
        )
    }

    private fun bindActions(
        context: Context,
        remoteViews: RemoteViews,
        appWidgetId: Int,
        classify: String,
        snapshot: WidgetPlaybackSnapshot,
        providerClass: Class<*>
    ) {
        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_EXPAND_PLAYER, true)
        }

        remoteViews.setOnClickPendingIntent(
            R.id.widget_background_image,
            PendingIntent.getActivity(
                context,
                appWidgetId * REQUEST_MULTIPLIER + REQUEST_OPEN_PLAYER,
                mainIntent,
                pendingIntentFlags()
            )
        )

        remoteViews.setOnClickPendingIntent(
            R.id.widget_album_image,
            PendingIntent.getActivity(
                context,
                appWidgetId * REQUEST_MULTIPLIER + REQUEST_ALBUM,
                mainIntent,
                pendingIntentFlags()
            )
        )

        remoteViews.setOnClickPendingIntent(
            R.id.widget_previous,
            serviceAction(
                context = context,
                action = MusicPlaybackService.ACTION_PREVIOUS,
                requestCode = appWidgetId * REQUEST_MULTIPLIER + REQUEST_PREVIOUS
            )
        )

        remoteViews.setOnClickPendingIntent(
            R.id.widget_next,
            serviceAction(
                context = context,
                action = MusicPlaybackService.ACTION_NEXT,
                requestCode = appWidgetId * REQUEST_MULTIPLIER + REQUEST_NEXT
            )
        )

        remoteViews.setOnClickPendingIntent(
            R.id.widget_play,
            serviceAction(
                context = context,
                action = MusicPlaybackService.ACTION_TOGGLE_PLAY_PAUSE,
                requestCode = appWidgetId * REQUEST_MULTIPLIER + REQUEST_PLAY
            )
        )

        remoteViews.setOnClickPendingIntent(
            R.id.widget_pause,
            serviceAction(
                context = context,
                action = MusicPlaybackService.ACTION_TOGGLE_PLAY_PAUSE,
                requestCode = appWidgetId * REQUEST_MULTIPLIER + REQUEST_PAUSE
            )
        )

        remoteViews.setOnClickPendingIntent(
            R.id.widget_flipper_play_pause,
            serviceAction(
                context = context,
                action = MusicPlaybackService.ACTION_TOGGLE_PLAY_PAUSE,
                requestCode = appWidgetId * REQUEST_MULTIPLIER + REQUEST_FLIPPER_PLAY_PAUSE
            )
        )

        remoteViews.setOnClickPendingIntent(
            R.id.widget_mode,
            widgetBroadcast(
                context = context,
                providerClass = providerClass,
                requestCode = appWidgetId * REQUEST_MULTIPLIER + REQUEST_MODE,
                action = BaseMusicAppWidgetProvider.ACTION_TOGGLE_MODE,
                appWidgetId = appWidgetId
            )
        )

        remoteViews.setOnClickPendingIntent(
            R.id.widget_setting,
            PendingIntent.getActivity(
                context,
                appWidgetId * REQUEST_MULTIPLIER + REQUEST_SETTINGS,
                WidgetConfigActivity.intent(context, appWidgetId, classify),
                pendingIntentFlags()
            )
        )

        remoteViews.setOnClickPendingIntent(
            R.id.widget_favorite_selected,
            widgetBroadcast(
                context = context,
                providerClass = providerClass,
                requestCode = appWidgetId * REQUEST_MULTIPLIER + REQUEST_FAVORITE_SELECTED,
                action = BaseMusicAppWidgetProvider.ACTION_TOGGLE_FAVORITE,
                appWidgetId = appWidgetId
            )
        )

        remoteViews.setOnClickPendingIntent(
            R.id.widget_favorite_unselected,
            widgetBroadcast(
                context = context,
                providerClass = providerClass,
                requestCode = appWidgetId * REQUEST_MULTIPLIER + REQUEST_FAVORITE_UNSELECTED,
                action = BaseMusicAppWidgetProvider.ACTION_TOGGLE_FAVORITE,
                appWidgetId = appWidgetId
            )
        )

        remoteViews.setOnClickPendingIntent(
            R.id.widget_flipper_favorite,
            widgetBroadcast(
                context = context,
                providerClass = providerClass,
                requestCode = appWidgetId * REQUEST_MULTIPLIER + REQUEST_FLIPPER_FAVORITE,
                action = BaseMusicAppWidgetProvider.ACTION_TOGGLE_FAVORITE,
                appWidgetId = appWidgetId
            )
        )

        remoteViews.setOnClickPendingIntent(
            R.id.widget_visualizer,
            PendingIntent.getActivity(
                context,
                appWidgetId * REQUEST_MULTIPLIER + REQUEST_VISUALIZER,
                mainIntent,
                pendingIntentFlags()
            )
        )

        if (classify == CLASSIFY_LIST) {
            val intent = Intent(context, WidgetQueueService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }

            remoteViews.setRemoteAdapter(R.id.widget_queue, intent)
            remoteViews.setEmptyView(R.id.widget_queue, R.id.widget_queue_empty)

            remoteViews.setPendingIntentTemplate(
                R.id.widget_queue,
                widgetBroadcast(
                    context = context,
                    providerClass = providerClass,
                    requestCode = appWidgetId * REQUEST_MULTIPLIER + REQUEST_QUEUE_TEMPLATE,
                    action = BaseMusicAppWidgetProvider.ACTION_PLAY_QUEUE_INDEX,
                    appWidgetId = appWidgetId
                )
            )
        } else if (!snapshot.hasTrack) {
            remoteViews.setOnClickPendingIntent(
                R.id.widget_content,
                PendingIntent.getActivity(
                    context,
                    appWidgetId * REQUEST_MULTIPLIER + REQUEST_EMPTY_CONTENT,
                    mainIntent,
                    pendingIntentFlags()
                )
            )
        }
    }

    private fun tintControl(
        remoteViews: RemoteViews,
        viewId: Int,
        color: Int
    ) {
        remoteViews.setInt(viewId, "setColorFilter", color)
    }

    private fun loadArtwork(
        context: Context,
        track: Music?,
        artworkStyle: WidgetArtworkStyle,
        targetSizePx: Int
    ): Bitmap? {
        if (track == null) return null

        return runCatching {
            val source = track.albumPicture?.takeIf { it.isNotBlank() }
                ?: track.albumId.takeIf { it.isNotBlank() }?.let { albumId ->
                    "content://media/external/audio/albumart/$albumId"
                }
                ?: track.data

            if (source.isNullOrBlank()) {
                return@runCatching null
            }

            decodeSampledBitmap(
                context = context,
                source = source,
                requestedSizePx = targetSizePx.coerceAtLeast(1)
            )?.let { bitmap ->
                transformArtwork(
                    bitmap = bitmap,
                    artworkStyle = artworkStyle,
                    targetSizePx = targetSizePx.coerceAtLeast(1)
                )
            }
        }.getOrNull()
    }

    private fun transformArtwork(
        bitmap: Bitmap,
        artworkStyle: WidgetArtworkStyle,
        targetSizePx: Int
    ): Bitmap {
        return when (artworkStyle) {
            WidgetArtworkStyle.DEFAULT -> createSquareArtwork(bitmap, targetSizePx)
            WidgetArtworkStyle.ROUNDED -> createShapedArtwork(bitmap, targetSizePx, rounded = true)
            WidgetArtworkStyle.CIRCLE,
            WidgetArtworkStyle.CIRCLE_LARGE,
            WidgetArtworkStyle.CIRCLE_TRANSPARENT -> createShapedArtwork(
                bitmap,
                targetSizePx,
                rounded = false
            )
        }
    }

    private fun decodeSampledBitmap(
        context: Context,
        source: String,
        requestedSizePx: Int
    ): Bitmap? {
        val imageUri = source.toArtworkUri()
        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }

        openArtworkStream(context, imageUri)?.use { input ->
            BitmapFactory.decodeStream(input, null, bounds)
        } ?: return null

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null
        }

        val decodeSizePx = (requestedSizePx * DECODE_OVERSCAN_MULTIPLIER)
            .roundToInt()
            .coerceAtLeast(requestedSizePx)

        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(
                width = bounds.outWidth,
                height = bounds.outHeight,
                requestedSizePx = decodeSizePx
            )
            inPreferredConfig = Bitmap.Config.RGB_565
            inDither = true
        }

        return openArtworkStream(context, imageUri)?.use { input ->
            BitmapFactory.decodeStream(input, null, options)
        }
    }

    private fun calculateInSampleSize(
        width: Int,
        height: Int,
        requestedSizePx: Int
    ): Int {
        var sampleSize = 1

        while (width / sampleSize > requestedSizePx * 2 ||
            height / sampleSize > requestedSizePx * 2
        ) {
            sampleSize *= 2
        }

        return sampleSize.coerceAtLeast(1)
    }

    private fun createShapedArtwork(
        bitmap: Bitmap,
        targetSizePx: Int,
        rounded: Boolean
    ): Bitmap {
        val size = minOf(
            targetSizePx.coerceAtLeast(1),
            maxOf(bitmap.width, bitmap.height)
        )
        if (size <= 0) return bitmap

        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        val matrix = Matrix()
        val scale = maxOf(
            size / bitmap.width.toFloat(),
            size / bitmap.height.toFloat()
        )
        val scaledWidth = bitmap.width * scale
        val scaledHeight = bitmap.height * scale
        matrix.setScale(scale, scale)
        matrix.postTranslate(
            (size - scaledWidth) / 2f,
            (size - scaledHeight) / 2f
        )
        shader.setLocalMatrix(matrix)

        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isFilterBitmap = true
            this.shader = shader
        }

        if (rounded) {
            val radius = size * 0.12f
            canvas.drawRoundRect(
                RectF(0f, 0f, size.toFloat(), size.toFloat()),
                radius,
                radius,
                paint
            )
        } else {
            val radius = size / 2f
            canvas.drawCircle(radius, radius, radius, paint)
        }

        return output
    }

    private fun createSquareArtwork(
        bitmap: Bitmap,
        targetSizePx: Int
    ): Bitmap {
        val size = minOf(
            targetSizePx.coerceAtLeast(1),
            maxOf(bitmap.width, bitmap.height)
        )
        if (size <= 0) return bitmap

        val output = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isFilterBitmap = true
        }
        val scale = maxOf(
            size / bitmap.width.toFloat(),
            size / bitmap.height.toFloat()
        )
        val scaledWidth = bitmap.width * scale
        val scaledHeight = bitmap.height * scale
        val left = (size - scaledWidth) / 2f
        val top = (size - scaledHeight) / 2f

        canvas.drawColor(Color.BLACK)
        canvas.save()
        canvas.translate(left, top)
        canvas.scale(scale, scale)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        canvas.restore()

        return output
    }

    private fun openArtworkStream(
        context: Context,
        uri: Uri
    ): InputStream? {
        return context.contentResolver.openInputStream(uri)
    }

    private fun String.toArtworkUri(): Uri {
        return if (contains("://")) {
            Uri.parse(this)
        } else {
            Uri.fromFile(File(this))
        }
    }

    private fun artworkTargetSizePx(
        context: Context,
        classify: String
    ): Int {
        val sizeRes = when (classify) {
            "2*1" -> R.dimen.widget_4x1_height
            "3*2" -> R.dimen.widget_3x2_2_album_size
            "4*1" -> R.dimen.widget_4x1_height
            "4*2" -> R.dimen.widget_4x2_height
            "4*3" -> R.dimen.widget_4x3_height
            "4*4" -> R.dimen.widget_4x4_height
            CLASSIFY_LIST -> R.dimen.widget_queue_album_size
            else -> R.dimen.widget_4x1_height
        }

        return context.resources.getDimensionPixelSize(sizeRes)
            .coerceAtMost(MAX_WIDGET_ARTWORK_SIZE_PX)
    }

    private fun formatQueueInfo(snapshot: WidgetPlaybackSnapshot): String {
        val queueSize = snapshot.queue.size
        if (queueSize == 0) return "0/0"

        val current = (snapshot.currentIndex + 1)
            .coerceAtLeast(0)
            .coerceAtMost(queueSize)

        return "$current/$queueSize"
    }

    private fun serviceAction(
        context: Context,
        action: String,
        requestCode: Int
    ): PendingIntent {
        val intent = Intent(context, MusicPlaybackService::class.java)
            .setAction(action)

        return PendingIntent.getService(
            context,
            requestCode,
            intent,
            pendingIntentFlags()
        )
    }

    private fun widgetBroadcast(
        context: Context,
        providerClass: Class<*>,
        requestCode: Int,
        action: String,
        appWidgetId: Int
    ): PendingIntent {
        val intent = Intent(context, providerClass).apply {
            this.action = action
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }

        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            pendingIntentFlags()
        )
    }

    private fun pendingIntentFlags(): Int {
        val mutabilityFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE
        } else {
            0
        }

        return PendingIntent.FLAG_UPDATE_CURRENT or mutabilityFlag
    }

    private fun modeIcon(mode: Int): Int {
        return when (mode) {
            PlaybackMode.SINGLE -> R.drawable.widget_ic_mode_single
            PlaybackMode.LOOP_ALL -> R.drawable.widget_ic_mode_loop
            PlaybackMode.SHUFFLE_ALL -> R.drawable.widget_ic_mode_random
            else -> R.drawable.widget_ic_mode_order
        }
    }

    private fun defaultConfig(classify: String): WidgetConfig {
        val defaultStyle = WidgetCatalog.defaultStyle(classify)
        val defaultTheme = WidgetCatalog.defaultThemeOption()

        return WidgetConfig(
            classify = classify,
            styleKey = defaultStyle.styleKey,
            themeType = defaultTheme.themeType,
            themeIndex = defaultTheme.index,
            alpha = defaultTheme.alpha
        )
    }

    private const val CLASSIFY_LIST = "List"

    private const val REQUEST_MULTIPLIER = 100
    private const val REQUEST_OPEN_PLAYER = 1
    private const val REQUEST_PREVIOUS = 2
    private const val REQUEST_NEXT = 3
    private const val REQUEST_PLAY = 4
    private const val REQUEST_PAUSE = 5
    private const val REQUEST_MODE = 6
    private const val REQUEST_SETTINGS = 7
    private const val REQUEST_FAVORITE_SELECTED = 8
    private const val REQUEST_FAVORITE_UNSELECTED = 9
    private const val REQUEST_VISUALIZER = 10
    private const val REQUEST_QUEUE_TEMPLATE = 11
    private const val REQUEST_EMPTY_CONTENT = 12
    private const val REQUEST_FLIPPER_PLAY_PAUSE = 13
    private const val REQUEST_FLIPPER_FAVORITE = 14
    private const val REQUEST_ALBUM = 15

    private const val DECODE_OVERSCAN_MULTIPLIER = 1.25f
    private const val MAX_WIDGET_ARTWORK_SIZE_PX = 320

    private const val DARK_FOREGROUND_PRIMARY = -570425344
    private const val DARK_FOREGROUND_SECONDARY = -1979711488
    private const val LIGHT_FOREGROUND_SECONDARY = -1275068417
}
