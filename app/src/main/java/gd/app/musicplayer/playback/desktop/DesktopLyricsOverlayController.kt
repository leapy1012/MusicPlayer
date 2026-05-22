package gd.app.musicplayer.playback.desktop

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.ContextThemeWrapper
import android.widget.ImageView
import android.widget.TextView
import android.widget.ViewFlipper
import androidx.recyclerview.widget.RecyclerView
import gd.app.lib.model.lrc.renderer.StaticMessageLyricRenderer
import gd.app.lib.model.lrc.view.LyricView
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.util.ToastUtil
import gd.app.musicplayer.core.designsystem.view.DeskLrcDragLayout
import gd.app.musicplayer.core.designsystem.view.DeskLrcRootLayout
import gd.app.musicplayer.core.designsystem.view.SeekBar
import gd.app.musicplayer.core.datastore.DesktopLyricPreference
import gd.app.musicplayer.core.datastore.TrackLyricData
import gd.app.musicplayer.core.datastore.TrackLyricPreferenceStore
import gd.app.musicplayer.domain.model.MusicSet
import gd.app.musicplayer.playback.queue.MusicPlaybackState
import gd.app.musicplayer.ui.common.playback.PlayModeUiMapper
import gd.app.musicplayer.feature.lyrics.setLyricText
import gd.app.musicplayer.ui.shell.MainActivity
import gd.app.musicplayer.util.LyricsLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class DesktopLyricsOverlayController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val trackLyricPreferenceStore: TrackLyricPreferenceStore,
    private val callbacks: Callbacks
) : View.OnClickListener,
    SeekBar.OnSeekBarChangeListener,
    DeskLrcDragLayout.DeskLyricsDragListener,
    DeskLrcRootLayout.OnActionListener,
    Runnable {

    interface Callbacks {
        fun previous()
        fun next()
        fun togglePlayPause()
        fun cyclePlaybackMode()
        fun toggleFavorite()
        fun closeDesktopLyrics()
        fun lockDesktopLyrics()
        fun updatePreference(
            presetColorIndex: Int? = null,
            currentColorProgress: Int? = null,
            normalColorProgress: Int? = null,
            alpha: Float? = null,
            textSize: Int? = null,
            y: Int? = null
        )
        fun currentPlaybackMode(): Int
    }

    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var rootView: DeskLrcRootLayout? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private var lyricView: LyricView? = null
    private var modeButton: ImageView? = null
    private var favoriteButton: ImageView? = null
    private var playPauseButton: ImageView? = null
    private var settingLayout: View? = null
    private var viewFlipper: ViewFlipper? = null
    private var presetColorButton: TextView? = null
    private var customColorButton: TextView? = null
    private var currentColorSeekBar: SeekBar? = null
    private var normalColorSeekBar: SeekBar? = null
    private var alphaSeekBar: SeekBar? = null
    private var fontZoomOutButton: ImageView? = null
    private var fontZoomInButton: ImageView? = null
    private var presetColorAdapter: DesktopLyricPresetColorAdapter? = null

    private var preference = DesktopLyricPreference()
    private var appInForeground = false
    private val currentTrack = MutableStateFlow<TrackRequest?>(null)
    private var lyricsLoadJob: Job? = null
    private var currentLoadKey: LyricLoadKey? = null
    private var maxY = 0

    init {
        observeCurrentTrackLyricData()
    }

    fun renderPreference(preference: DesktopLyricPreference) {
        val previousPreference = this.preference
        this.preference = preference

        applyVisibilityState(previousPreference = previousPreference)
    }

    fun renderAppForeground(isForeground: Boolean) {
        appInForeground = isForeground
        applyVisibilityState(previousPreference = preference)
    }

    private fun applyVisibilityState(previousPreference: DesktopLyricPreference) {
        if (preference.visible && !appInForeground && hasOverlayPermission()) {
            ensureAdded()
            applyPreference(
                preference = preference,
                showLockToast = rootView?.parent != null &&
                        previousPreference.locked != preference.locked
            )
        } else {
            remove()
        }
    }

    fun renderPlaybackState(state: MusicPlaybackState) {
        val root = rootView ?: return
        val lyric = lyricView ?: return
        val currentTrack = state.currentTrack

        playPauseButton?.isSelected = state.isPlaying
        favoriteButton?.isSelected = currentTrack?.playlistId == MusicSet.FAVORITES
        modeButton?.setImageResource(PlayModeUiMapper.iconRes(callbacks.currentPlaybackMode()))

        this.currentTrack.value = currentTrack?.let { TrackRequest(it.id, it.data) }

        lyric.setCurrentTime(state.positionMs)

        if (root.parent != null) {
            root.post(::updateMaxY)
        }
    }

    fun onConfigurationChanged() {
        rootView?.let { root ->
            root.findViewById<TextView>(R.id.desk_lrc_preset_color).setText(R.string.preset_color)
            root.findViewById<TextView>(R.id.desk_lrc_custom_color).setText(R.string.custom_color)
            root.findViewById<TextView>(R.id.title1).setText(R.string.present)
            root.findViewById<TextView>(R.id.title2).setText(R.string.next_sentence)
            root.findViewById<TextView>(R.id.title3).setText(R.string.transparency)
        }
    }

    fun destroy() {
        lyricsLoadJob?.cancel()
        lyricsLoadJob = null
        remove()
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.desk_lrc_cancel -> callbacks.closeDesktopLyrics()
            R.id.desk_lrc_favorite -> callbacks.toggleFavorite()
            R.id.desk_lrc_font_zoom_in -> updateFontSize(increase = true)
            R.id.desk_lrc_font_zoom_out -> updateFontSize(increase = false)
            R.id.desk_lrc_local -> openApp()
            R.id.desk_lrc_lock -> callbacks.lockDesktopLyrics()
            R.id.desk_lrc_mode -> callbacks.cyclePlaybackMode()
            R.id.desk_lrc_next -> callbacks.next()
            R.id.desk_lrc_play_pause -> callbacks.togglePlayPause()
            R.id.desk_lrc_preset_color -> showPresetColors()
            R.id.desk_lrc_previous -> callbacks.previous()
            R.id.desk_lrc_custom_color -> showCustomColors()
            R.id.desk_lrc_setting -> toggleSettings()
        }
    }

    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
        if (!fromUser) return

        when (seekBar) {
            currentColorSeekBar -> {
                val color = DesktopLyricColorUtils.interpolate(
                    DesktopLyricColorUtils.currentGradientColors,
                    progress / 100f
                )
                seekBar.setThumbOverlayColor(color)
                lyricView?.setCurrentTextColor(color)
            }

            normalColorSeekBar -> {
                val color = DesktopLyricColorUtils.interpolate(
                    DesktopLyricColorUtils.normalGradientColors,
                    progress / 100f
                )
                seekBar.setThumbOverlayColor(color)
                lyricView?.setNormalTextColor(color)
            }

            alphaSeekBar -> {
                lyricView?.alpha = ((progress + MIN_ALPHA_PROGRESS_OFFSET) / 100f)
                    .coerceIn(MIN_ALPHA, MAX_ALPHA)
            }
        }
    }

    override fun onStartTrackingTouch(seekBar: SeekBar) = Unit

    override fun onStopTrackingTouch(seekBar: SeekBar) {
        when (seekBar) {
            currentColorSeekBar -> {
                presetColorAdapter?.clearSelection()
                callbacks.updatePreference(
                    presetColorIndex = CUSTOM_COLOR_INDEX,
                    currentColorProgress = seekBar.getProgress()
                )
            }

            normalColorSeekBar -> {
                presetColorAdapter?.clearSelection()
                callbacks.updatePreference(
                    presetColorIndex = CUSTOM_COLOR_INDEX,
                    normalColorProgress = seekBar.getProgress()
                )
            }

            alphaSeekBar -> {
                callbacks.updatePreference(
                    alpha = ((seekBar.getProgress() + MIN_ALPHA_PROGRESS_OFFSET) / 100f)
                        .coerceIn(MIN_ALPHA, MAX_ALPHA)
                )
            }
        }
    }

    override fun onQuickClick(view: View) {
        val controlsVisible = rootView?.findViewById<View>(R.id.desk_lrc_top)?.visibility == View.VISIBLE
        setControlsVisible(!controlsVisible)
    }

    override fun onDrag(view: View, deltaY: Float) {
        val params = layoutParams ?: return
        val root = rootView ?: return
        if (root.parent == null) return

        params.y = (params.y + deltaY.toInt()).coerceIn(0, maxY)
        windowManager.updateViewLayout(root, params)
        callbacks.updatePreference(y = params.y)
    }

    override fun onTouchStart(view: View) {
        rootView?.removeCallbacks(this)
    }

    override fun onTouchEnd(view: View) {
        rootView?.postDelayed(this, AUTO_HIDE_DELAY_MS)
    }

    override fun onQuickOutsideTap(view: View) {
        if (rootView?.findViewById<View>(R.id.desk_lrc_top)?.visibility == View.VISIBLE) {
            setControlsVisible(false)
        }
    }

    override fun run() {
        setControlsVisible(false, scheduleNextHide = false)
    }

    private fun ensureAdded() {
        val root = rootView ?: createRootView()
        if (root.parent != null) return

        val params = layoutParams ?: createLayoutParams().also { layoutParams = it }
        applyInitialY(root, params)

        runCatching {
            windowManager.addView(root, params)
            root.post {
                updateMaxY()
                root.postDelayed(this, AUTO_HIDE_DELAY_MS)
            }
        }
    }

    private fun createRootView(): DeskLrcRootLayout {
        val themedContext = ContextThemeWrapper(context, R.style.AppTheme)
        val root = LayoutInflater.from(themedContext)
            .inflate(R.layout.layout_desk_lrc, null) as DeskLrcRootLayout

        rootView = root
        lyricView = root.findViewById(R.id.desk_lrc_view)
        modeButton = root.findViewById(R.id.desk_lrc_mode)
        favoriteButton = root.findViewById(R.id.desk_lrc_favorite)
        playPauseButton = root.findViewById(R.id.desk_lrc_play_pause)
        settingLayout = root.findViewById(R.id.setting_layout)
        viewFlipper = root.findViewById(R.id.viewFlipper)
        presetColorButton = root.findViewById(R.id.desk_lrc_preset_color)
        customColorButton = root.findViewById(R.id.desk_lrc_custom_color)
        currentColorSeekBar = root.findViewById(R.id.desk_lrc_current_color_seekBar)
        normalColorSeekBar = root.findViewById(R.id.desk_lrc_normal_color_seekBar)
        alphaSeekBar = root.findViewById(R.id.desk_lrc_alpha_seekBar)
        fontZoomOutButton = root.findViewById(R.id.desk_lrc_font_zoom_out)
        fontZoomInButton = root.findViewById(R.id.desk_lrc_font_zoom_in)

        root.findViewById<View>(R.id.desk_lrc_previous).setOnClickListener(this)
        root.findViewById<View>(R.id.desk_lrc_next).setOnClickListener(this)
        root.findViewById<View>(R.id.desk_lrc_local).setOnClickListener(this)
        root.findViewById<View>(R.id.desk_lrc_cancel).setOnClickListener(this)
        root.findViewById<View>(R.id.desk_lrc_lock).setOnClickListener(this)
        root.findViewById<View>(R.id.desk_lrc_setting).setOnClickListener(this)
        playPauseButton?.setOnClickListener(this)
        favoriteButton?.setOnClickListener(this)
        modeButton?.setOnClickListener(this)
        presetColorButton?.setOnClickListener(this)
        customColorButton?.setOnClickListener(this)
        fontZoomOutButton?.setOnClickListener(this)
        fontZoomInButton?.setOnClickListener(this)

        currentColorSeekBar?.setMax(100)
        normalColorSeekBar?.setMax(100)
        alphaSeekBar?.setMax(60)
        currentColorSeekBar?.setProgressDrawable(
            DesktopLyricColorUtils.gradientDrawable(
                themedContext,
                DesktopLyricColorUtils.currentGradientColors
            )
        )
        normalColorSeekBar?.setProgressDrawable(
            DesktopLyricColorUtils.gradientDrawable(
                themedContext,
                DesktopLyricColorUtils.normalGradientColors
            )
        )
        currentColorSeekBar?.setOnSeekBarChangeListener(this)
        normalColorSeekBar?.setOnSeekBarChangeListener(this)
        alphaSeekBar?.setOnSeekBarChangeListener(this)

        presetColorAdapter = DesktopLyricPresetColorAdapter(
            recyclerView = root.findViewById<RecyclerView>(R.id.recyclerview),
            lyricView = root.findViewById(R.id.desk_lrc_view),
            currentColorSeekBar = root.findViewById(R.id.desk_lrc_current_color_seekBar),
            normalColorSeekBar = root.findViewById(R.id.desk_lrc_normal_color_seekBar),
            onPresetSelected = { preset, index ->
                callbacks.updatePreference(
                    presetColorIndex = index,
                    currentColorProgress = preset.currentProgress,
                    normalColorProgress = preset.normalProgress
                )
            }
        )

        root.setOnActionListener(this)
        root.findViewById<DeskLrcDragLayout>(R.id.desk_lrc_parent_layout)
            .setDeskLyricsDragListener(this)

        if (root.measuredHeight == 0) {
            root.measure(
                View.MeasureSpec.UNSPECIFIED,
                View.MeasureSpec.UNSPECIFIED
            )
        }

        return root
    }

    private fun createLayoutParams(): WindowManager.LayoutParams {
        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        return WindowManager.LayoutParams().apply {
            type = overlayType
            format = PixelFormat.RGBA_8888
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            horizontalMargin = context.resources.displayMetrics.density * 10f
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }
    }

    private fun applyPreference(
        preference: DesktopLyricPreference,
        showLockToast: Boolean
    ) {
        val lyric = lyricView ?: return

        applyLock(preference.locked, showToast = showLockToast)

        if (preference.presetColorIndex in DesktopLyricPresetColorAdapter.presetColors.indices) {
            presetColorAdapter?.render(preference.presetColorIndex, applySelected = true)
            showPresetColors()
        } else {
            showCustomColors(applyPreferenceColors = true)
        }

        val alpha = preference.alpha.coerceIn(MIN_ALPHA, MAX_ALPHA)
        alphaSeekBar?.setProgress(((alpha * 100).toInt() - MIN_ALPHA_PROGRESS_OFFSET).coerceIn(0, 60))
        lyric.alpha = alpha

        lyric.updateTextSize(preference.textSize.coerceIn(MIN_TEXT_SIZE, MAX_TEXT_SIZE), false)
        updateFontButtons(preference.textSize)
    }

    private fun applyCustomColors(preference: DesktopLyricPreference) {
        val currentProgress = preference.currentColorProgress.coerceIn(0, 100)
        val normalProgress = preference.normalColorProgress.coerceIn(0, 100)
        val currentColor = DesktopLyricColorUtils.interpolate(
            DesktopLyricColorUtils.currentGradientColors,
            currentProgress / 100f
        )
        val normalColor = DesktopLyricColorUtils.interpolate(
            DesktopLyricColorUtils.normalGradientColors,
            normalProgress / 100f
        )

        currentColorSeekBar?.setProgress(currentProgress)
        normalColorSeekBar?.setProgress(normalProgress)
        currentColorSeekBar?.setThumbOverlayColor(currentColor)
        normalColorSeekBar?.setThumbOverlayColor(normalColor)
        lyricView?.setCurrentTextColor(currentColor)
        lyricView?.setNormalTextColor(normalColor)
    }

    private fun showPresetColors() {
        presetColorButton?.isSelected = true
        customColorButton?.isSelected = false
        viewFlipper?.displayedChild = 0
    }

    private fun showCustomColors() {
        showCustomColors(applyPreferenceColors = false)
    }

    private fun showCustomColors(applyPreferenceColors: Boolean) {
        presetColorButton?.isSelected = false
        customColorButton?.isSelected = true
        viewFlipper?.displayedChild = 1
        if (applyPreferenceColors) {
            applyCustomColors(preference)
        }
    }

    private fun toggleSettings() {
        val root = rootView ?: return
        val settingsVisible = settingLayout?.visibility == View.VISIBLE
        settingLayout?.visibility = if (settingsVisible) View.GONE else View.VISIBLE
        root.findViewById<View>(R.id.desk_lrc_setting_bg).visibility =
            if (settingsVisible) View.GONE else View.VISIBLE

        if (!settingsVisible) {
            if (preference.presetColorIndex in DesktopLyricPresetColorAdapter.presetColors.indices) {
                showPresetColors()
            } else {
                showCustomColors()
            }
        }

        updateMaxY()
        scheduleAutoHide()
    }

    private fun updateFontSize(increase: Boolean) {
        val current = preference.textSize
        val next = if (increase) {
            (current + TEXT_SIZE_STEP).coerceAtMost(MAX_TEXT_SIZE)
        } else {
            (current - TEXT_SIZE_STEP).coerceAtLeast(MIN_TEXT_SIZE)
        }

        if (next == current) return

        preference = preference.copy(textSize = next)
        lyricView?.updateTextSize(next, true)
        updateFontButtons(next)
        callbacks.updatePreference(textSize = next)
        updateMaxY()
    }

    private fun updateFontButtons(textSize: Int) {
        fontZoomInButton?.isSelected = textSize >= MAX_TEXT_SIZE
        fontZoomOutButton?.isSelected = textSize <= MIN_TEXT_SIZE
    }

    private fun setControlsVisible(
        visible: Boolean,
        scheduleNextHide: Boolean = true
    ) {
        val root = rootView ?: return
        val params = layoutParams ?: return

        if (visible) {
            root.findViewById<View>(R.id.desk_lrc_top).visibility = View.VISIBLE
            root.findViewById<View>(R.id.desk_lrc_bottom).visibility = View.VISIBLE
            root.findViewById<View>(R.id.desk_lrc_content)
                .setBackgroundResource(R.drawable.shape_desk_lrc)
            root.findViewById<View>(R.id.desk_lrc_setting_bg).visibility = View.GONE
            params.flags = params.flags and
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv() and
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL.inv()
        } else {
            root.findViewById<View>(R.id.desk_lrc_top).visibility = View.INVISIBLE
            root.findViewById<View>(R.id.desk_lrc_bottom).visibility = View.GONE
            root.findViewById<View>(R.id.desk_lrc_content)
                .setBackgroundResource(android.R.color.transparent)
            settingLayout?.visibility = View.GONE
            root.findViewById<View>(R.id.desk_lrc_setting_bg).visibility = View.GONE
            params.flags = params.flags or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        }

        if (preference.locked) {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }

        if (root.parent != null) {
            runCatching { windowManager.updateViewLayout(root, params) }
        }

        if (scheduleNextHide) {
            scheduleAutoHide()
        }
    }

    private fun applyLock(locked: Boolean, showToast: Boolean) {
        val root = rootView ?: return
        val params = layoutParams ?: return

        params.flags = if (locked) {
            params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        } else {
            params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            params.alpha = if (locked) LOCKED_WINDOW_ALPHA else UNLOCKED_WINDOW_ALPHA
        }

        if (locked) {
            setControlsVisible(false)
        } else if (root.parent != null) {
            runCatching { windowManager.updateViewLayout(root, params) }
        }

        if (showToast) {
            ToastUtil.show(
                context,
                if (locked) R.string.desk_lrc_locked_tips_2 else R.string.desk_lrc_unlocked_tips
            )
        }
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
            lyricView?.setLyricText(null)
            lyricView?.showStaticMessage(context.getString(R.string.no_lrc_1))
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
        lyricView?.setLyricText(null)

        lyricsLoadJob = scope.launch {
            val result = LyricsLoader.load(
                context = context,
                trackId = track.trackId,
                audioPath = track.source
            )
            if (currentLoadKey == loadKey) {
                if (result.hasLyrics) {
                    lyricView?.setLyricText(result.text)
                } else {
                    lyricView?.showStaticMessage(context.getString(R.string.no_lrc_1))
                }
            }
        }
    }

    private fun LyricView.showStaticMessage(message: String) {
        setLyricRenderer(StaticMessageLyricRenderer(message))
    }

    private fun applyInitialY(root: View, params: WindowManager.LayoutParams) {
        updateMaxY()
        params.y = if (preference.y >= 0) {
            preference.y.coerceIn(0, maxY)
        } else {
            if (root.measuredHeight > 0) {
                ((context.resources.displayMetrics.heightPixels - root.measuredHeight) / 2)
                    .coerceAtLeast(0)
            } else {
                0
            }
        }
    }

    private fun updateMaxY() {
        val root = rootView ?: return
        maxY = (context.resources.displayMetrics.heightPixels - root.height)
            .coerceAtLeast(0)
        layoutParams?.let { params ->
            if (params.y > maxY) {
                params.y = maxY
                if (root.parent != null) {
                    runCatching { windowManager.updateViewLayout(root, params) }
                }
                callbacks.updatePreference(y = params.y)
            }
        }
    }

    private fun scheduleAutoHide() {
        val root = rootView ?: return
        root.removeCallbacks(this)
        root.postDelayed(this, AUTO_HIDE_DELAY_MS)
    }

    private fun openApp() {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(MainActivity.EXTRA_EXPAND_PLAYER, true)
        }
        context.startActivity(intent)
    }

    private fun remove() {
        val root = rootView ?: return
        root.removeCallbacks(this)
        if (root.parent != null) {
            runCatching { windowManager.removeViewImmediate(root) }
            if (root.parent != null) {
                runCatching { (root.parent as? ViewGroup)?.removeView(root) }
            }
        }
    }

    private fun hasOverlayPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                Settings.canDrawOverlays(context)
    }

    companion object {
        private const val AUTO_HIDE_DELAY_MS = 5_000L
        private const val CUSTOM_COLOR_INDEX = -1
        private const val MIN_ALPHA = 0.4f
        private const val MAX_ALPHA = 1f
        private const val MIN_ALPHA_PROGRESS_OFFSET = 40
        private const val MIN_TEXT_SIZE = 14
        private const val MAX_TEXT_SIZE = 24
        private const val TEXT_SIZE_STEP = 2
        private const val LOCKED_WINDOW_ALPHA = 0.7f
        private const val UNLOCKED_WINDOW_ALPHA = 1f
    }

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
}
