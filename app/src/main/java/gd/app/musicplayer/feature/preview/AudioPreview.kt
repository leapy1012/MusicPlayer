package gd.app.musicplayer.feature.preview

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toDrawable
import gd.app.lib.view.MarqueeTextView
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.dpToPx
import gd.app.musicplayer.core.common.util.FastBlur
import gd.app.musicplayer.core.designsystem.dialog.BaseDialog
import gd.app.musicplayer.core.designsystem.drawable.ScaledOverlayDrawable
import gd.app.musicplayer.core.designsystem.theme.ThemePalette
import gd.app.musicplayer.core.designsystem.theme.accentColor
import gd.app.musicplayer.domain.model.Music
import kotlin.math.max

/**
 * Lightweight external-file preview dialog. Plays via [AudioPreviewPlayer] without
 * launching the main library / queue session.
 *
 * Theme/URI/callbacks live on [Config] so [createContentView] can use them during
 * [BaseDialog] construction (subclass constructor properties are not assigned yet).
 */
class AudioPreview(
    context: Context,
    config: Config
) : BaseDialog(context, config),
    AudioPreviewPlayer.Listener,
    SeekBar.OnSeekBarChangeListener {

    class Config(
        val audioUri: Uri,
        val themePalette: ThemePalette,
        val onOpenInPlayer: (Uri) -> Unit
    ) : BaseDialog.Config() {
        init {
            dialogLayoutRes = R.layout.dialog_audio_preview
            cancelable = true
            canceledOnTouchOutside = true
            dimAmount = 0.5f
            allowTouchEventPassThrough = false
            backgroundDrawable = Color.TRANSPARENT.toDrawable()
            cornerRadii = null
        }
    }

    private val previewConfig: Config get() = config as Config

    private var player: AudioPreviewPlayer? = null

    private lateinit var shellView: View
    private lateinit var bodyView: View
    private lateinit var artworkFrame: View
    private lateinit var artworkView: AppCompatImageView
    private lateinit var titleView: MarqueeTextView
    private lateinit var artistView: MarqueeTextView
    private lateinit var seekBar: SeekBar
    private lateinit var positionView: TextView
    private lateinit var durationView: TextView
    private lateinit var playPauseView: AppCompatImageView
    private lateinit var closeButton: AppCompatImageView
    private lateinit var openPlayerButton: AppCompatImageView
    private lateinit var headerDivider: View

    private var openPlayerRequested = false
    private var durationMs = 0
    private var shellCornerRadiusPx = 0f
    private var fallbackShellBackground: android.graphics.drawable.Drawable? = null

    fun currentUri(): Uri = player?.currentUri() ?: previewConfig.audioUri

    override fun createContentView(context: Context, config: BaseDialog.Config): View {
        val preview = config as Config
        return View.inflate(context, R.layout.dialog_audio_preview, null).also { root ->
            (root as? ViewGroup)?.let { group ->
                group.clipChildren = false
                group.clipToPadding = false
            }
            bindViews(root)
            applyTheme(context, preview.themePalette)
            bindMetadata(context, preview.audioUri)
            bindActions()
            startPlayer(context, preview.audioUri)
        }
    }

    override fun show() {
        dialogContainer.clipChildren = false
        dialogContainer.clipToPadding = false
        contentHost.clipChildren = false
        contentHost.clipToPadding = false
        super.show()
    }

    override fun dismiss() {
        releasePlayer()
        val shouldOpenInPlayer = openPlayerRequested
        openPlayerRequested = false
        if (shouldOpenInPlayer) {
            previewConfig.onOpenInPlayer(currentUri())
        }
        super.dismiss()
    }

    fun dismissQuietly() {
        openPlayerRequested = false
        dismiss()
    }

    override fun onPlayingChanged(isPlaying: Boolean) {
        if (!::playPauseView.isInitialized) return
        playPauseView.isSelected = isPlaying
    }

    override fun onProgressChanged(positionMs: Int, durationMs: Int) {
        if (!::seekBar.isInitialized) return
        this.durationMs = durationMs
        if (durationMs > 0) {
            seekBar.progress = (positionMs.toLong() * SEEK_MAX / durationMs).toInt()
        }
        positionView.text = formatTime(positionMs)
        durationView.text = formatTime(durationMs)
    }

    override fun onPrepared(durationMs: Int) {
        if (!::durationView.isInitialized) return
        this.durationMs = durationMs
        seekBar.progress = 0
        positionView.text = formatTime(0)
        durationView.text = formatTime(durationMs)
        playPauseView.isSelected = true
        playPauseView.isEnabled = true
        seekBar.isEnabled = true
    }

    override fun onCompletion() {
        if (!::playPauseView.isInitialized) return
        playPauseView.isSelected = false
        seekBar.progress = 0
        positionView.text = formatTime(0)
    }

    override fun onError() {
        if (!::playPauseView.isInitialized) return
        playPauseView.isSelected = false
        playPauseView.isEnabled = false
        seekBar.isEnabled = false
    }

    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
        if (!fromUser || durationMs <= 0) return
        val positionMs = (progress.toLong() * durationMs / SEEK_MAX).toInt()
        positionView.text = formatTime(positionMs)
    }

    override fun onStartTrackingTouch(seekBar: SeekBar) {
        player?.beginUserSeek()
    }

    override fun onStopTrackingTouch(seekBar: SeekBar) {
        if (durationMs <= 0) {
            player?.endUserSeek(0)
            return
        }
        val positionMs = (seekBar.progress.toLong() * durationMs / SEEK_MAX).toInt()
        player?.endUserSeek(positionMs)
    }

    private fun bindViews(root: View) {
        shellView = root.findViewById(R.id.audio_preview_shell)
        bodyView = root.findViewById(R.id.audio_preview_body)
        artworkFrame = root.findViewById(R.id.audio_preview_artwork_frame)
        artworkView = root.findViewById(R.id.audio_preview_artwork)
        titleView = root.findViewById(R.id.audio_preview_title)
        artistView = root.findViewById(R.id.audio_preview_artist)
        seekBar = root.findViewById(R.id.audio_preview_seek)
        positionView = root.findViewById(R.id.audio_preview_position)
        durationView = root.findViewById(R.id.audio_preview_duration)
        playPauseView = root.findViewById(R.id.audio_preview_play_pause)
        closeButton = root.findViewById(R.id.audio_preview_close)
        openPlayerButton = root.findViewById(R.id.audio_preview_open_player)
        headerDivider = root.findViewById(R.id.audio_preview_header_divider)

        seekBar.max = SEEK_MAX
        seekBar.setOnSeekBarChangeListener(this)
        clipViewToCircle(artworkFrame)
    }

    private fun clipViewToCircle(view: View) {
        view.post {
            view.outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(v: View, outline: Outline) {
                    outline.setOval(0, 0, v.width, v.height)
                }
            }
            view.clipToOutline = true
            view.invalidateOutline()
        }
    }

    private fun roundShellCorners() {
        shellView.post {
            shellView.outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(v: View, outline: Outline) {
                    outline.setRoundRect(0, 0, v.width, v.height, shellCornerRadiusPx)
                }
            }
            shellView.clipToOutline = true
            shellView.invalidateOutline()
        }
    }

    private fun applyTheme(context: Context, palette: ThemePalette) {
        val surfaceLight = palette.isDialogSurfaceLight()
        val artCircleColor = if (surfaceLight) 0xFFE8EAED.toInt() else 0xFF3A424C.toInt()
        val primaryText = palette.getDialogTitleColor()
        val secondaryText = palette.getDialogSecondaryTextColor()
        val iconColor = if (surfaceLight) 0xDE000000.toInt() else 0xDEFFFFFF.toInt()
        val accent = palette.accentColor
        val dividerColor = ColorUtils.setAlphaComponent(iconColor, 0x26)
        shellCornerRadiusPx = context.dpToPx(24f).toFloat()

        // One background for the whole preview (circular art + controls).
        bodyView.background = null
        fallbackShellBackground = palette.getDialogSurfaceDrawable(context).mutate()
        shellView.background = fallbackShellBackground
        roundShellCorners()

        artworkFrame.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(artCircleColor)
        }

        titleView.setTextColor(primaryText)
        artistView.setTextColor(secondaryText)
        positionView.setTextColor(secondaryText)
        durationView.setTextColor(secondaryText)
        headerDivider.setBackgroundColor(dividerColor)

        // Line play/pause icons stay white over the art / glass circle.
        playPauseView.imageTintList = ColorStateList.valueOf(Color.WHITE)
        closeButton.imageTintList = ColorStateList.valueOf(secondaryText)
        openPlayerButton.imageTintList = ColorStateList.valueOf(secondaryText)

        seekBar.progressTintList = ColorStateList.valueOf(accent)
        seekBar.thumbTintList = ColorStateList.valueOf(accent)
        seekBar.progressBackgroundTintList = ColorStateList.valueOf(
            ColorUtils.setAlphaComponent(iconColor, 0x33)
        )
    }

    private fun bindMetadata(context: Context, uri: Uri) {
        showArtworkPlaceholder()
        applyArtworkBackground(null)

        val metadata = readMetadata(context, uri)
        titleView.text = metadata.title
        artistView.text = metadata.artist
        metadata.artwork?.let { bitmap ->
            showArtworkBitmap(bitmap)
            applyArtworkBackground(bitmap)
        }
    }

    private fun applyArtworkBackground(artwork: Bitmap?) {
        if (artwork == null) {
            shellView.background = fallbackShellBackground
            roundShellCorners()
            return
        }

        val resources = shellView.resources
        val sample = scaleForBlur(artwork)
        val blurred = FastBlur.blur(sample, BLUR_RADIUS, canReuseInBitmap = sample !== artwork)
            ?: sample
        val overlay = ScaledOverlayDrawable(BitmapDrawable(resources, blurred)).apply {
            setForegroundOverlayColor(ART_BG_OVERLAY)
        }
        shellView.background = overlay
        roundShellCorners()
    }

    private fun scaleForBlur(source: Bitmap): Bitmap {
        val maxSide = 128
        val largest = max(source.width, source.height)
        if (largest <= maxSide) {
            return source.copy(source.config ?: Bitmap.Config.ARGB_8888, true)
        }
        val scale = maxSide.toFloat() / largest
        val w = (source.width * scale).toInt().coerceAtLeast(1)
        val h = (source.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, w, h, true)
    }

    private fun showArtworkPlaceholder() {
        artworkView.setImageResource(R.drawable.vector_audio_preview_note)
        artworkView.imageTintList = ColorStateList.valueOf(Color.WHITE)
        artworkView.scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
        val pad = artworkView.resources.displayMetrics.density.times(26).toInt()
        artworkView.setPadding(pad, pad, pad, pad)
    }

    private fun showArtworkBitmap(bitmap: Bitmap) {
        artworkView.imageTintList = null
        artworkView.setPadding(0, 0, 0, 0)
        artworkView.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
        artworkView.setImageBitmap(bitmap)
        clipViewToCircle(artworkView)
    }

    private fun bindActions() {
        playPauseView.setOnClickListener { player?.toggle() }
        closeButton.setOnClickListener { dismiss() }
        openPlayerButton.setOnClickListener {
            openPlayerRequested = true
            dismiss()
        }
    }

    private fun startPlayer(context: Context, uri: Uri) {
        player = AudioPreviewPlayer(context, uri).also {
            it.setListener(this)
            it.prepare()
        }
    }

    private fun releasePlayer() {
        player?.release()
        player = null
    }

    private data class PreviewMetadata(
        val title: String,
        val artist: String,
        val artwork: Bitmap?
    )

    private companion object {
        const val SEEK_MAX = 1000
        const val BLUR_RADIUS = 24
        const val ART_BG_OVERLAY = 0xBF2A3139.toInt()

        fun readMetadata(context: Context, uri: Uri): PreviewMetadata {
            var title: String? = null
            var artist: String? = null
            var artwork: Bitmap? = null

            runCatching {
                val retriever = MediaMetadataRetriever()
                try {
                    setRetrieverDataSource(retriever, context, uri)
                    title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                    artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    val bytes = retriever.embeddedPicture
                    if (bytes != null) {
                        artwork = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    }
                } finally {
                    retriever.release()
                }
            }

            if (title.isNullOrBlank()) {
                title = queryDisplayName(context, uri)
            }

            return PreviewMetadata(
                title = title?.takeIf { it.isNotBlank() } ?: Music.UNKNOWN_TITLE,
                artist = artist?.takeIf { it.isNotBlank() } ?: Music.UNKNOWN_ARTIST,
                artwork = artwork
            )
        }

        fun setRetrieverDataSource(
            retriever: MediaMetadataRetriever,
            context: Context,
            uri: Uri
        ) {
            val path = resolveLocalPath(uri)
            if (path != null) {
                runCatching {
                    retriever.setDataSource(path)
                    return
                }
            }
            retriever.setDataSource(context, uri)
        }

        fun resolveLocalPath(uri: Uri): String? {
            if (uri.scheme == "file") {
                return uri.path?.takeIf { java.io.File(it).canRead() }
            }
            // MTK filebrowser: content://…/external/Music/... → /storage/emulated/0/Music/...
            val segments = uri.pathSegments.orEmpty()
            val externalIndex = segments.indexOfFirst { it.equals("external", ignoreCase = true) }
            if (externalIndex >= 0 && externalIndex < segments.lastIndex) {
                val relative = segments.drop(externalIndex + 1).joinToString("/")
                if (relative.isNotBlank()) {
                    val candidate = "/storage/emulated/0/$relative"
                    if (java.io.File(candidate).canRead()) return candidate
                }
            }
            return null
        }

        fun queryDisplayName(context: Context, uri: Uri): String? {
            if (uri.scheme == "content") {
                runCatching {
                    context.contentResolver.query(
                        uri,
                        arrayOf(OpenableColumns.DISPLAY_NAME),
                        null,
                        null,
                        null
                    )?.use { cursor ->
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index >= 0 && cursor.moveToFirst()) {
                            return cursor.getString(index)
                        }
                    }
                }
            }
            return uri.lastPathSegment?.substringAfterLast('/')
        }

        fun formatTime(timeMs: Int): String {
            val totalSeconds = (timeMs.coerceAtLeast(0) / 1000)
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return "%d:%02d".format(minutes, seconds)
        }
    }
}
