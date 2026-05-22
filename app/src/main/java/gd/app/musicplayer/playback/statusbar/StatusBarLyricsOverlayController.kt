package gd.app.musicplayer.playback.statusbar

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.core.graphics.ColorUtils
import gd.app.lib.view.MarqueeTextView
import gd.app.musicplayer.R
import gd.app.musicplayer.core.datastore.StatusBarLyricPreference
import gd.app.musicplayer.core.datastore.StatusBarLyricPreferenceStore
import gd.app.musicplayer.core.datastore.TrackLyricData
import gd.app.musicplayer.core.datastore.TrackLyricPreferenceStore
import gd.app.musicplayer.playback.queue.MusicPlaybackState
import gd.app.musicplayer.util.LyricsLoader
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class StatusBarLyricsOverlayController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val trackLyricPreferenceStore: TrackLyricPreferenceStore,
    private val callbacks: Callbacks
) {

    interface Callbacks {
        fun togglePlayPause()
    }

    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var preference = StatusBarLyricPreference()
    private var textView: MarqueeTextView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var lyricLines: List<TimedLyricLine> = emptyList()
    private val currentTrack = MutableStateFlow<TrackRequest?>(null)
    private var lastDisplayText = ""
    private var hideState = HIDE_STATE_HIDDEN
    private var lyricsLoadJob: Job? = null
    private var currentLoadKey: LyricLoadKey? = null
    private var currentState = MusicPlaybackState()

    init {
        observeCurrentTrackLyricData()
    }

    private val hidePausedRunnable = Runnable {
        if (hideState == HIDE_STATE_PAUSED_TIP) {
            hideState = HIDE_STATE_HIDDEN
            applyVisibilityState()
        }
    }

    fun renderPreference(preference: StatusBarLyricPreference) {
        this.preference = preference

        if (preference.enabled && hasOverlayPermission()) {
            ensureAdded()
            applyStyle()
            updateLayout()
            renderPlaybackState(currentState)
        } else {
            remove()
        }
    }

    fun renderPlaybackState(state: MusicPlaybackState) {
        currentState = state
        if (!preference.enabled || !hasOverlayPermission()) {
            remove()
            return
        }

        ensureAdded()

        val track = state.currentTrack
        currentTrack.value = track?.let { TrackRequest(it.id, it.data) }

        val displayText = resolveDisplayText(state)
        if (displayText != lastDisplayText) {
            lastDisplayText = displayText
            setOverlayText(displayText)
        }

        hideState = if (state.isPlaying) {
            HIDE_STATE_VISIBLE
        } else if (preference.showPaused) {
            HIDE_STATE_VISIBLE
        } else {
            if (hideState == HIDE_STATE_VISIBLE) {
                HIDE_STATE_PAUSED_TIP
            } else {
                hideState
            }
        }
        applyVisibilityState()
    }

    fun onConfigurationChanged() {
        updateLayout()
    }

    fun destroy() {
        lyricsLoadJob?.cancel()
        lyricsLoadJob = null
        remove()
    }

    private fun ensureAdded() {
        if (textView == null) {
            textView = createTextView()
        }

        if (layoutParams == null) {
            layoutParams = createLayoutParams()
        }

        val view = textView ?: return
        if (view.parent != null) return

        runCatching {
            updateLayout()
            windowManager.addView(view, layoutParams)
        }
    }

    private fun remove() {
        val view = textView ?: return
        view.removeCallbacks(hidePausedRunnable)
        if (view.parent == null) return
        runCatching {
            windowManager.removeView(view)
        }
    }

    private fun createTextView(): MarqueeTextView {
        val horizontalPadding = dp(6f)
        val verticalPadding = dp(1f)

        return MarqueeTextView(context).apply {
            setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
            textAlignment = View.TEXT_ALIGNMENT_GRAVITY
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setOnClickListener {
                if (preference.clickable) {
                    callbacks.togglePlayPause()
                }
            }
            applyStyle()
        }
    }

    private fun createLayoutParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams().apply {
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
            gravity = Gravity.START or Gravity.TOP
            format = PixelFormat.TRANSLUCENT
            screenOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            flags = BASE_FLAGS
            alpha = 0.7f
        }
    }

    private fun applyStyle() {
        val view = textView ?: return
        val color = preference.textColor

        view.setTextColor(color)
        view.textSize = lerp(10f, 16f, preference.fontSizeRatio)
        view.gravity = preference.gravity
        view.background = GradientDrawable().apply {
            cornerRadius = dp(100f).toFloat()
            setColor(ColorUtils.setAlphaComponent(color, (preference.alphaRatio * 51f).roundToInt()))
        }

        layoutParams?.flags = if (preference.clickable) {
            BASE_FLAGS and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        } else {
            BASE_FLAGS or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
    }

    private fun updateLayout() {
        val view = textView ?: return
        val params = layoutParams ?: return

        val metrics = context.resources.displayMetrics
        val screenWidth = metrics.widthPixels
        val screenHeight = metrics.heightPixels
        val widthRatio = lerp(0.2f, 1.0f, preference.widthRatio)
        val textHeight = view.textSize + view.lineSpacingExtra +
                (view.lineSpacingMultiplier * view.textSize) +
                view.paddingTop + view.paddingBottom + 0.5f

        if (widthRatio >= 0.999f) {
            params.width = WindowManager.LayoutParams.MATCH_PARENT
            params.x = 0
        } else {
            params.width = (screenWidth * widthRatio).roundToInt()
            params.x = ((screenWidth - params.width) * preference.xRatio).roundToInt()
        }

        params.height = textHeight.roundToInt().coerceAtLeast(dp(16f))
        params.y = lerp(
            dp(2f).toFloat(),
            (screenHeight - params.height - dp(2f)).toFloat().coerceAtLeast(0f),
            preference.yRatio
        ).roundToInt()

        if (view.parent != null) {
            runCatching {
                windowManager.updateViewLayout(view, params)
            }
        }
    }

    private fun resolveDisplayText(state: MusicPlaybackState): String {
        val track = state.currentTrack ?: return ""
        if (preference.contentType == StatusBarLyricPreferenceStore.CONTENT_TYPE_TITLE) {
            return track.title
        }

        if (lyricLines.isEmpty()) {
            return track.title
        }

        val position = state.positionMs.coerceAtLeast(0L)
        var current: TimedLyricLine? = null
        for (line in lyricLines) {
            if (line.timeMs > position) break
            if (line.text.isNotBlank()) {
                current = line
            }
        }
        return current?.text ?: lyricLines.firstOrNull { it.text.isNotBlank() }?.text ?: track.title
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun observeCurrentTrackLyricData() {
        scope.launch {
            currentTrack
                .flatMapLatest { request ->
                    if (request == null) {
                        flowOf(null)
                    } else {
                        trackLyricPreferenceStore.observeTrackLyricData(request.trackId)
                            .map { data -> request to data }
                    }
                }
                .collect { requestAndData ->
                    if (requestAndData == null) {
                        loadLyrics(null, null)
                    } else {
                        loadLyrics(requestAndData.first, requestAndData.second)
                    }
                }
        }
    }

    private fun loadLyrics(
        track: TrackRequest?,
        lyricData: TrackLyricData?
    ) {
        if (track == null) {
            lyricsLoadJob?.cancel()
            currentLoadKey = null
            lyricLines = emptyList()
            lastDisplayText = ""
            setOverlayText("")
            return
        }

        val loadKey = LyricLoadKey(
            trackId = track.trackId,
            source = track.source,
            lyricPath = lyricData?.path,
            lyricRevision = lyricData?.revision ?: 0
        )
        if (loadKey == currentLoadKey) return
        currentLoadKey = loadKey

        lyricsLoadJob?.cancel()
        lyricLines = emptyList()

        lyricsLoadJob = scope.launch {
            val result = LyricsLoader.load(
                context = context,
                trackId = track.trackId,
                audioPath = track.source
            )
            if (currentLoadKey != loadKey) return@launch
            lyricLines = parseTimedLyrics(result.text)
            val displayText = resolveDisplayText(currentState)
            lastDisplayText = displayText
            setOverlayText(displayText)
        }
    }

    private fun parseTimedLyrics(raw: String?): List<TimedLyricLine> {
        if (raw.isNullOrBlank()) return emptyList()
        val result = mutableListOf<TimedLyricLine>()
        raw.lineSequence().forEach { line ->
            val matches = TIMESTAMP_REGEX.findAll(line).toList()
            if (matches.isEmpty()) return@forEach

            val text = TIMESTAMP_REGEX.replace(line, "").trim()
            matches.forEach { match ->
                val minute = match.groupValues[1].toLongOrNull() ?: 0L
                val second = match.groupValues[2].toLongOrNull() ?: 0L
                val fraction = match.groupValues[3]
                val millis = when (fraction.length) {
                    0 -> 0L
                    1 -> (fraction.toLongOrNull() ?: 0L) * 100L
                    2 -> (fraction.toLongOrNull() ?: 0L) * 10L
                    else -> fraction.take(3).toLongOrNull() ?: 0L
                }
                result += TimedLyricLine(
                    timeMs = minute * 60_000L + second * 1_000L + millis,
                    text = text
                )
            }
        }
        return result.sortedBy { it.timeMs }
    }

    private fun applyVisibilityState() {
        val view = textView ?: return
        view.removeCallbacks(hidePausedRunnable)

        when (hideState) {
            HIDE_STATE_VISIBLE -> {
                setOverlayText(lastDisplayText)
                view.visibility = View.VISIBLE
            }

            HIDE_STATE_PAUSED_TIP -> {
                setOverlayText(context.getString(R.string.sbar_lyric_hide_tips))
                view.visibility = View.VISIBLE
                view.postDelayed(hidePausedRunnable, PAUSED_HIDE_DELAY_MS)
            }

            else -> {
                view.visibility = View.INVISIBLE
            }
        }
    }

    private fun setOverlayText(value: String) {
        val view = textView ?: return
        if (view.text?.toString() == value) return
        view.text = value
    }

    private fun hasOverlayPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                Settings.canDrawOverlays(context)
    }

    private fun dp(value: Float): Int {
        return (value * context.resources.displayMetrics.density).roundToInt()
    }

    private fun lerp(start: Float, end: Float, ratio: Float): Float {
        return start + (end - start) * ratio.coerceIn(0f, 1f)
    }

    private data class TimedLyricLine(
        val timeMs: Long,
        val text: String
    )

    private data class TrackRequest(
        val trackId: Long,
        val source: String?
    )

    private data class LyricLoadKey(
        val trackId: Long,
        val source: String?,
        val lyricPath: String?,
        val lyricRevision: Int
    )

    private companion object {
        private const val HIDE_STATE_VISIBLE = 0
        private const val HIDE_STATE_PAUSED_TIP = 1
        private const val HIDE_STATE_HIDDEN = 2
        private const val PAUSED_HIDE_DELAY_MS = 3_000L

        private const val BASE_FLAGS =
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

        private val TIMESTAMP_REGEX =
            Regex("""\[(\d{1,2}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    }
}
