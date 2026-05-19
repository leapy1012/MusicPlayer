package gd.app.musicplayer.ui.shortcut

import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.graphics.drawable.IconCompat
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.albumArtSource
import gd.app.musicplayer.core.common.extension.dpToPx
import gd.app.musicplayer.core.common.extension.screenWidth
import gd.app.musicplayer.domain.model.MusicSet
import gd.app.musicplayer.ui.common.model.resolvePlaceholderRes

private const val ACTION_MUSIC_SET_SHORTCUT = "gd.app.musicplayer.action.MUSIC_SET_SHORTCUT"
private const val EXTRA_MUSIC_SET_KIND = "shortcut_music_set_kind"
private const val EXTRA_MUSIC_SET_NAME = "shortcut_music_set_name"
private const val EXTRA_MUSIC_SET_ARTWORK = "shortcut_music_set_artwork"
private const val EXTRA_MUSIC_SET_ARTIST = "shortcut_music_set_artist"
private const val EXTRA_MUSIC_SET_MUSIC_COUNT = "shortcut_music_set_music_count"
private const val EXTRA_MUSIC_SET_ALBUM_COUNT = "shortcut_music_set_album_count"
private const val EXTRA_MUSIC_SET_FOLDER_PATH = "shortcut_music_set_folder_path"
private const val EXTRA_MUSIC_SET_YEAR = "shortcut_music_set_year"
private const val EXTRA_MUSIC_SET_DATE = "shortcut_music_set_date"
private const val EXTRA_MUSIC_SET_GENRES = "shortcut_music_set_genres"
private const val EXTRA_MUSIC_SET_SORT = "shortcut_music_set_sort"
private const val EXTRA_MUSIC_SET_SETUP_TIME = "shortcut_music_set_setup_time"
private const val EXTRA_MUSIC_SET_ALBUM_ID = "shortcut_music_set_album_id"
private const val EXTRA_MUSIC_SET_S_PIC = "shortcut_music_set_s_pic"

object MusicSetShortcutHelper {

    const val EXTRA_SOURCE = "shortcut_source"
    const val EXTRA_MUSIC_SET_ID = "shortcut_music_set_id"

    private const val SOURCE_MUSIC_SET = "music_set"

    fun isPinShortcutSupported(context: Context): Boolean {
        return AppShortcutManager.isPinShortcutSupported(context)
    }

    fun buildShortcut(
        context: Context,
        musicSet: MusicSet,
        title: String,
        @DrawableRes iconResId: Int = musicSet.resolvePlaceholderRes(useTextVariant = false)
    ): ShortcutInfoCompat {
        val launchIntent = buildLaunchIntent(context, musicSet)
        val shortcutTitle = title.ifBlank { context.resolveShortcutTitle(musicSet) }

        return AppShortcutManager.buildShortcut(
            context = context,
            id = shortcutId(musicSet),
            shortLabel = shortcutTitle,
            longLabel = shortcutTitle,
            intent = launchIntent,
            icon = buildShortcutIcon(context, musicSet, iconResId)
        )
    }

    fun requestPinnedShortcut(
        context: Context,
        musicSet: MusicSet,
        title: String,
        @DrawableRes iconResId: Int = musicSet.resolvePlaceholderRes(useTextVariant = false),
        callback: IntentSender? = null
    ): Boolean {
        val shortcut = buildShortcut(
            context = context,
            musicSet = musicSet,
            title = title,
            iconResId = iconResId
        )
        return AppShortcutManager.requestPinnedShortcut(context, shortcut, callback)
    }

    fun extractMusicSetId(intent: Intent?): Long? {
        if (intent?.getStringExtra(EXTRA_SOURCE) != SOURCE_MUSIC_SET) return null
        if (intent.hasExtra(EXTRA_MUSIC_SET_ID).not()) return null
        return intent.getLongExtra(EXTRA_MUSIC_SET_ID, Long.MIN_VALUE)
            .takeUnless { it == Long.MIN_VALUE }
    }

    fun extractMusicSet(intent: Intent?): MusicSet? {
        if (intent?.action != ACTION_MUSIC_SET_SHORTCUT) return null
        if (intent.getStringExtra(EXTRA_SOURCE) != SOURCE_MUSIC_SET) return null
        return restoreMusicSet(intent)
    }

    fun buildLaunchIntent(
        context: Context,
        musicSet: MusicSet
    ): Intent {
        return Intent(context, MusicSetShortcutActivity::class.java).apply {
            action = ACTION_MUSIC_SET_SHORTCUT
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_SOURCE, SOURCE_MUSIC_SET)
            putExtra(EXTRA_MUSIC_SET_ID, musicSet.id)
            putMusicSetFields(musicSet)
        }
    }

    private fun Intent.putMusicSetFields(musicSet: MusicSet) {
        putExtra(EXTRA_MUSIC_SET_KIND, musicSet::class.java.simpleName)
        putExtra(EXTRA_MUSIC_SET_NAME, musicSet.name)
        putExtra(EXTRA_MUSIC_SET_ARTWORK, musicSet.albumArt)

        when (musicSet) {
            is MusicSet.Album -> {
                putExtra(EXTRA_MUSIC_SET_ARTIST, musicSet.artist)
                putExtra(EXTRA_MUSIC_SET_MUSIC_COUNT, musicSet.musicCount)
                putExtra(EXTRA_MUSIC_SET_YEAR, musicSet.year)
                putExtra(EXTRA_MUSIC_SET_DATE, musicSet.date)
                putExtra(EXTRA_MUSIC_SET_GENRES, musicSet.genres)
            }
            is MusicSet.Artist -> {
                putExtra(EXTRA_MUSIC_SET_MUSIC_COUNT, musicSet.musicCount)
                putExtra(EXTRA_MUSIC_SET_ALBUM_COUNT, musicSet.albumCount)
            }
            is MusicSet.Genre -> {
                putExtra(EXTRA_MUSIC_SET_MUSIC_COUNT, musicSet.musicCount)
            }
            is MusicSet.Folder -> {
                putExtra(EXTRA_MUSIC_SET_FOLDER_PATH, musicSet.folderPath)
                putExtra(EXTRA_MUSIC_SET_MUSIC_COUNT, musicSet.musicCount)
                putExtra(EXTRA_MUSIC_SET_DATE, musicSet.date)
            }
            is MusicSet.Playlist -> {
                putExtra(EXTRA_MUSIC_SET_MUSIC_COUNT, musicSet.musicCount)
                putExtra(EXTRA_MUSIC_SET_SORT, musicSet.sort)
                putExtra(EXTRA_MUSIC_SET_SETUP_TIME, musicSet.setup_time)
                putExtra(EXTRA_MUSIC_SET_ALBUM_ID, musicSet.album_id)
                putExtra(EXTRA_MUSIC_SET_S_PIC, musicSet.s_pic)
            }
            else -> Unit
        }
    }

    private fun restoreMusicSet(intent: Intent): MusicSet? {
        val id = intent.getLongExtra(EXTRA_MUSIC_SET_ID, MusicSet.UNKNOWN_ID)
        val name = intent.getStringExtra(EXTRA_MUSIC_SET_NAME).orEmpty()
        val albumArt = intent.getStringExtra(EXTRA_MUSIC_SET_ARTWORK)

        return when (intent.getStringExtra(EXTRA_MUSIC_SET_KIND)) {
            MusicSet.Album::class.java.simpleName -> MusicSet.Album(
                id = id,
                name = name,
                albumArt = albumArt,
                artist = intent.getStringExtra(EXTRA_MUSIC_SET_ARTIST).orEmpty(),
                musicCount = intent.getIntExtra(EXTRA_MUSIC_SET_MUSIC_COUNT, 0),
                year = intent.getIntExtra(EXTRA_MUSIC_SET_YEAR, 0),
                date = intent.getLongExtra(EXTRA_MUSIC_SET_DATE, 0L),
                genres = intent.getStringExtra(EXTRA_MUSIC_SET_GENRES).orEmpty()
            )
            MusicSet.Artist::class.java.simpleName -> MusicSet.Artist(
                id = id,
                name = name,
                musicCount = intent.getIntExtra(EXTRA_MUSIC_SET_MUSIC_COUNT, 0),
                albumCount = intent.getIntExtra(EXTRA_MUSIC_SET_ALBUM_COUNT, 0),
                albumArt = albumArt
            )
            MusicSet.Genre::class.java.simpleName -> MusicSet.Genre(
                id = id,
                name = name,
                albumArt = albumArt,
                musicCount = intent.getIntExtra(EXTRA_MUSIC_SET_MUSIC_COUNT, 0)
            )
            MusicSet.Folder::class.java.simpleName -> MusicSet.Folder(
                id = id,
                name = name,
                folderPath = intent.getStringExtra(EXTRA_MUSIC_SET_FOLDER_PATH).orEmpty(),
                musicCount = intent.getIntExtra(EXTRA_MUSIC_SET_MUSIC_COUNT, 0),
                albumArt = albumArt,
                date = intent.getLongExtra(EXTRA_MUSIC_SET_DATE, 0L)
            )
            MusicSet.Playlist::class.java.simpleName -> MusicSet.Playlist(
                id = id,
                name = name,
                albumArt = albumArt,
                musicCount = intent.getIntExtra(EXTRA_MUSIC_SET_MUSIC_COUNT, 0),
                sort = intent.getLongExtra(EXTRA_MUSIC_SET_SORT, 0L),
                setup_time = intent.getLongExtra(EXTRA_MUSIC_SET_SETUP_TIME, 0L),
                album_id = intent.getLongExtra(EXTRA_MUSIC_SET_ALBUM_ID, 0L),
                s_pic = intent.getStringExtra(EXTRA_MUSIC_SET_S_PIC).orEmpty()
            )
            MusicSet.RecentlyAdded::class.java.simpleName -> MusicSet.RecentlyAdded
            MusicSet.RecentlyPlayed::class.java.simpleName -> MusicSet.RecentlyPlayed
            MusicSet.MostPlayed::class.java.simpleName -> MusicSet.MostPlayed
            MusicSet.Favorites::class.java.simpleName -> MusicSet.Favorites
            else -> null
        }
    }

    private fun shortcutId(musicSet: MusicSet): String {
        return "music_set_${musicSet::class.java.simpleName}_${musicSet.id}_${musicSet.name.hashCode()}"
    }

    private fun Context.resolveShortcutTitle(musicSet: MusicSet): String {
        return when (musicSet) {
            is MusicSet.Favorites -> getString(R.string.favorite)
            is MusicSet.RecentlyPlayed -> getString(R.string.recent_play)
            is MusicSet.RecentlyAdded -> getString(R.string.recent_add)
            is MusicSet.MostPlayed -> getString(R.string.most_play)
            is MusicSet.Tracks -> getString(R.string.all_songs)
            else -> musicSet.name.ifBlank { getString(R.string.music_player) }
        }
    }

    private fun buildShortcutIcon(
        context: Context,
        musicSet: MusicSet,
        @DrawableRes fallbackResId: Int
    ): IconCompat {
        val size = (context.screenWidth / 5).coerceAtLeast(context.dpToPx(48f))
        val artwork = decodeArtworkBitmap(context, musicSet.albumArtSource(), size)
        val bitmap = if (artwork != null) {
            cropCenterSquare(artwork, size)
        } else {
            renderFallbackIcon(context, fallbackResId, size)
        }
        return IconCompat.createWithBitmap(bitmap)
    }

    private fun decodeArtworkBitmap(
        context: Context,
        source: String?,
        size: Int
    ): Bitmap? {
        if (source.isNullOrBlank()) return null

        return runCatching {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }

            openArtworkStream(context, source)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }

            if (options.outWidth <= 0 || options.outHeight <= 0) return null

            val target = size.coerceAtLeast(1)
            val sampleSize = calculateInSampleSize(options.outWidth, options.outHeight, target)
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            openArtworkStream(context, source)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            }
        }.getOrNull()
    }

    private fun openArtworkStream(
        context: Context,
        source: String
    ) = if (source.startsWith("content://") || source.startsWith("file://")) {
        context.contentResolver.openInputStream(Uri.parse(source))
    } else {
        java.io.File(source).takeIf { it.exists() }?.inputStream()
    }

    private fun calculateInSampleSize(
        width: Int,
        height: Int,
        targetSize: Int
    ): Int {
        var sampleSize = 1
        var halfWidth = width / 2
        var halfHeight = height / 2

        while (halfWidth / sampleSize >= targetSize && halfHeight / sampleSize >= targetSize) {
            sampleSize *= 2
        }

        return sampleSize
    }

    private fun cropCenterSquare(
        source: Bitmap,
        size: Int
    ): Bitmap {
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val sourceSize = minOf(source.width, source.height)
        val left = (source.width - sourceSize) / 2
        val top = (source.height - sourceSize) / 2
        val sourceRect = Rect(left, top, left + sourceSize, top + sourceSize)
        val targetRect = Rect(0, 0, size, size)
        Canvas(output).drawBitmap(source, sourceRect, targetRect, Paint(Paint.ANTI_ALIAS_FLAG))
        if (source !== output && !source.isRecycled) {
            source.recycle()
        }
        return output
    }

    private fun renderFallbackIcon(
        context: Context,
        @DrawableRes drawableResId: Int,
        size: Int
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        val canvas = Canvas(bitmap)
        canvas.drawColor(SHORTCUT_FALLBACK_BACKGROUND_COLOR)

        val drawable = ContextCompat.getDrawable(context, drawableResId)
        if (drawable != null) {
            drawable.setBounds(0, 0, size, size)
            drawable.draw(canvas)
        } else {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                alpha = 220
            }
            canvas.drawRoundRect(
                RectF(0f, 0f, size.toFloat(), size.toFloat()),
                size * 0.08f,
                size * 0.08f,
                paint
            )
        }
        return bitmap
    }

    private const val SHORTCUT_FALLBACK_BACKGROUND_COLOR = -14342875
}
