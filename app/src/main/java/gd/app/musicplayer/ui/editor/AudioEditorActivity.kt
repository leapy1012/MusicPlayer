package gd.app.musicplayer.ui.editor

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Selection
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.AppCompatEditText
import androidx.appcompat.widget.Toolbar
import androidx.core.widget.ImageViewCompat
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.dpToPx
import gd.app.musicplayer.core.common.extension.applyLengthFilter
import gd.app.musicplayer.core.common.extension.extractValidatedText
import gd.app.musicplayer.core.common.extension.hideKeyboard
import gd.app.musicplayer.core.common.extension.parcelable
import gd.app.musicplayer.core.common.extension.showKeyboardDelayed
import gd.app.musicplayer.core.common.extension.startActivityCompat
import gd.app.musicplayer.core.common.util.ToastUtil
import gd.app.musicplayer.core.designsystem.dialog.MaterialDialogConfigFactory
import gd.app.musicplayer.core.designsystem.dialog.MessageDialog
import gd.app.musicplayer.core.designsystem.dialog.createMessageDialogConfig
import gd.app.musicplayer.core.designsystem.dialog.showMessageDialog
import gd.app.musicplayer.core.mediastore.MediaStoreMusicImporter
import gd.app.musicplayer.databinding.ActivityAudioEditorBinding
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.domain.usecase.scan.UpsertScannedTracksUseCase
import gd.app.musicplayer.playback.PlaybackController
import gd.app.musicplayer.ui.common.base.BaseActivity
import gd.app.musicplayer.ui.common.base.setupEdgeToEdgeToolbar
import gd.app.musicplayer.ui.editor.data.AudioTrimRepository
import gd.app.musicplayer.ui.editor.data.AudioTrimNameExistsException
import gd.app.musicplayer.ui.editor.data.WaveformExtractor
import gd.app.musicplayer.ui.editor.model.AudioClipRange
import gd.app.musicplayer.ui.editor.model.WaveformData
import gd.app.musicplayer.ui.editor.waveform.SoundWaveView
import gd.app.musicplayer.ui.editor.waveform.TimeEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min

@AndroidEntryPoint
class AudioEditorActivity : BaseActivity(),
    Toolbar.OnMenuItemClickListener,
    SoundWaveView.OnClipChangedListener,
    TimeEditText.OnInputTimeChangedListener,
    SoundPreviewPlayer.Listener {

    private lateinit var binding: ActivityAudioEditorBinding

    private var track: Music? = null
    private var waveformData: WaveformData? = null

    @Inject
    lateinit var upsertScannedTracksUseCase: UpsertScannedTracksUseCase

    @Inject
    lateinit var audioTrimRepository: AudioTrimRepository

    @Inject
    lateinit var playbackController: PlaybackController

    @Inject
    lateinit var materialDialogConfigFactory: MaterialDialogConfigFactory

    private var previewPlayer: SoundPreviewPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityAudioEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        track = intent.parcelable(EXTRA_MUSIC)

        if (track == null || track?.data.isNullOrBlank()) {
            ToastUtil.show(this, R.string.audio_editor_error)
            finish()
            return
        }

        pauseCurrentPlaybackIfNeeded()
        setupToolbar()
        bindViews()

        if (savedInstanceState == null) {
            loadWaveform()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return onMenuItemClick(item) || super.onOptionsItemSelected(item)
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        if (item.itemId == R.id.menu_save) {
            stopPreview()

            if (binding.audioEditorPlay.isEnabled) {
                requestSave()
            }

            return true
        }

        return false
    }

    override fun onDestroy() {
        stopPreview()
        releasePlayer()
        super.onDestroy()
    }

    override fun onStop() {
        stopPreview()
        super.onStop()
    }

    override fun onClipStartChanged(timeMs: Int) {
        val safeTime = timeMs.coerceAtLeast(0)

        binding.audioEditorStartTime.setTime(safeTime)
        updateLengthText()

        previewPlayer?.setClipStart(safeTime)
    }

    override fun onClipEndChanged(timeMs: Int) {
        val safeTime = timeMs.coerceAtLeast(0)

        binding.audioEditorEndTime.setTime(safeTime)
        updateLengthText()

        previewPlayer?.setClipEnd(safeTime)
    }

    override fun onPlayingChanged(isPlaying: Boolean) {
        binding.audioEditorPlay.isSelected = isPlaying
        binding.audioEditorWave.setSeek(true)
    }

    override fun onProgressChanged(positionMs: Int) {
        binding.audioEditorWave.setProgress(positionMs)
    }

    override fun onSeekRequested(timeMs: Int) {
        val clipStart = binding.audioEditorWave.getClipLeftMilliseconds()
        val clipEnd = binding.audioEditorWave.getClipRightMilliseconds()

        ensurePlayer()

        val safeTime = timeMs.coerceIn(clipStart, clipEnd)
        previewTo(safeTime)
    }

    override fun onInputTimeChanged(
        timeEditText: TimeEditText,
        rawText: String,
        timeMs: Int
    ) {
        if (timeEditText === binding.audioEditorStartTime) {
            val clipEnd = binding.audioEditorWave.getClipRightMilliseconds()
            val adjustedTime = min(timeMs, clipEnd)

            binding.audioEditorWave.setClipLeft(adjustedTime, false)
            binding.audioEditorStartTime.setTime(adjustedTime)
        } else if (timeEditText === binding.audioEditorEndTime) {
            val clipStart = binding.audioEditorWave.getClipLeftMilliseconds()
            val adjustedTime = if (rawText.isEmpty()) {
                clipStart
            } else {
                max(timeMs, clipStart)
            }

            binding.audioEditorWave.setClipRight(adjustedTime)
            binding.audioEditorEndTime.setTime(adjustedTime)
        }

        updateLengthText()
    }

    override fun onInvalidTimeInput(
        timeEditText: TimeEditText,
        rawText: String
    ) {
        ToastUtil.show(this, R.string.input_error)
    }

    private fun setupToolbar() {
        setupEdgeToEdgeToolbar(
            root = binding.root,
            statusBarView = binding.statusBarSpace,
            toolbar = binding.toolbar,
            bottomPaddingView = binding.bottomControl as View
        )

        binding.toolbar.title = track?.title ?: getString(R.string.audio_editor_title)
        binding.toolbar.menu.clear()
        binding.toolbar.inflateMenu(R.menu.menu_activity_audio_editor)
        binding.toolbar.setOnMenuItemClickListener(this)
    }

    private fun pauseCurrentPlaybackIfNeeded() {
        if (playbackController.state.value.isPlaying) {
            playbackController.pause()
        }
    }

    private fun bindViews() {
        applyReferenceControlRendering()

        binding.audioEditorWave.setOnClipChangedListener(this)
        binding.audioEditorStartTime.setOnInputTimeChangedListener(this)
        binding.audioEditorEndTime.setOnInputTimeChangedListener(this)

        binding.audioEditorPlay.setOnClickListener {
            togglePreview()
        }

        binding.audioEditorZoomIn.setOnClickListener {
            binding.audioEditorWave.zoomIn()
            refreshZoomButtons()
        }

        binding.audioEditorZoomOut.setOnClickListener {
            binding.audioEditorWave.zoomOut()
            refreshZoomButtons()
        }

        binding.audioEditorStart.setOnClickListener {
            markStartFromPlayback()
        }

        binding.audioEditorEnd.setOnClickListener {
            markEndFromPlayback()
        }

        binding.audioEditorStartMinus.setOnClickListener {
            val currentStart = binding.audioEditorWave.getClipLeftMilliseconds()
            val newStart = max(0, currentStart - SMALL_STEP_MS)

            binding.audioEditorWave.setClipLeft(newStart, false)
            onClipStartChanged(binding.audioEditorWave.getClipLeftMilliseconds())
        }

        binding.audioEditorStartPlus.setOnClickListener {
            val currentStart = binding.audioEditorWave.getClipLeftMilliseconds()
            val clipEnd = binding.audioEditorWave.getClipRightMilliseconds()
            val newStart = min(clipEnd, currentStart + SMALL_STEP_MS)

            binding.audioEditorWave.setClipLeft(newStart, false)
            onClipStartChanged(binding.audioEditorWave.getClipLeftMilliseconds())
        }

        binding.audioEditorEndMinus.setOnClickListener {
            val clipStart = binding.audioEditorWave.getClipLeftMilliseconds()
            val currentEnd = binding.audioEditorWave.getClipRightMilliseconds()
            val newEnd = max(clipStart, currentEnd - SMALL_STEP_MS)

            binding.audioEditorWave.setClipRight(newEnd)
            onClipEndChanged(binding.audioEditorWave.getClipRightMilliseconds())
        }

        binding.audioEditorEndPlus.setOnClickListener {
            val duration = binding.audioEditorWave.getDuration()
            val currentEnd = binding.audioEditorWave.getClipRightMilliseconds()
            val newEnd = min(duration, currentEnd + SMALL_STEP_MS)

            binding.audioEditorWave.setClipRight(newEnd)
            onClipEndChanged(binding.audioEditorWave.getClipRightMilliseconds())
        }

        bindRepeatedSeek(binding.audioEditorPrevious, -LARGE_STEP_MS)
        bindRepeatedSeek(binding.audioEditorNext, LARGE_STEP_MS)

        setEditorEnabled(false)
    }

    private fun bindRepeatedSeek(view: View, deltaMs: Int) {
        view.setOnTouchListener(
            RepeatTouchListener(
                repeatDelayMs = REPEATED_SEEK_DELAY_MS,
                onRepeat = { seekPreviewBy(deltaMs) }
            )
        )
    }

    private fun applyReferenceControlRendering() {
        binding.audioEditorStart.background = createClipMarkerButtonBackground()
        binding.audioEditorEnd.background = createClipMarkerButtonBackground()

        val zoomTint = AppCompatResources.getColorStateList(
            this,
            R.drawable.selector_image_disable
        )
        ImageViewCompat.setImageTintList(binding.audioEditorZoomIn, zoomTint)
        ImageViewCompat.setImageTintList(binding.audioEditorZoomOut, zoomTint)

        binding.audioEditorPlay.setImageResource(R.drawable.vector_trim_play_pause_selector)
    }

    private fun createClipMarkerButtonBackground(): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(4f).toFloat()
            setColor(0x33FFFFFF)
            setStroke(dpToPx(1.5f).coerceAtLeast(1), Color.WHITE)
        }
    }

    private fun loadWaveform() {
        val path = track?.data ?: return

        setEditorEnabled(false)

        lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                runCatching {
                    WaveformExtractor.load(path)
                }.getOrNull()
            }

            if (loaded == null) {
                ToastUtil.show(this@AudioEditorActivity, R.string.audio_editor_error)
                finish()
                return@launch
            }

            waveformData = loaded

            binding.audioEditorInfo.text = loaded.infoText()
            binding.audioEditorWave.setWaveformData(loaded)

            binding.audioEditorStartTime.setMinTime(0)
            binding.audioEditorStartTime.setMaxTime(loaded.durationMs)

            binding.audioEditorEndTime.setMinTime(0)
            binding.audioEditorEndTime.setMaxTime(loaded.durationMs)

            binding.audioEditorStartTime.setTime(0)
            binding.audioEditorEndTime.setTime(loaded.durationMs)

            updateLengthText()
            refreshZoomButtons()
            ensurePlayer()
            setEditorEnabled(true)
        }
    }

    private fun ensurePlayer() {
        if (previewPlayer != null) return

        val music = track ?: return
        previewPlayer = SoundPreviewPlayer(music) {
            setDataSource(requireNotNull(music.data))
        }.apply {
            setListener(this@AudioEditorActivity)
            setClipStart(binding.audioEditorWave.getClipLeftMilliseconds())
            setClipEnd(binding.audioEditorWave.getClipRightMilliseconds())
        }
    }

    private fun togglePreview() {
        ensurePlayer()

        val player = previewPlayer ?: return
        if (!player.isReady()) return

        if (player.isPlaying()) {
            pausePreview(resetToClipStart = false)
            return
        }

        val clipStart = binding.audioEditorWave.getClipLeftMilliseconds()
        val clipEnd = binding.audioEditorWave.getClipRightMilliseconds()
        val currentPosition = player.currentPosition()

        if (currentPosition < clipStart || currentPosition >= clipEnd) {
            binding.audioEditorWave.setProgress(clipStart)
            player.playFrom(clipStart)
        } else {
            player.toggle()
        }
    }

    private fun pausePreview(resetToClipStart: Boolean) {
        val player = previewPlayer ?: return
        player.pause()

        if (resetToClipStart) {
            val clipStart = binding.audioEditorWave.getClipLeftMilliseconds()
            player.seekTo(clipStart)
            binding.audioEditorWave.setProgress(clipStart)
        }
    }

    private fun stopPreview() {
        pausePreview(resetToClipStart = false)
    }

    private fun releasePlayer() {
        previewPlayer?.release()
        previewPlayer = null
    }

    private fun previewTo(positionMs: Int) {
        ensurePlayer()

        val player = previewPlayer ?: return
        if (!player.isReady()) return

        val safePosition = positionMs.coerceIn(
            0,
            binding.audioEditorWave.getDuration()
        )

        player.playFrom(safePosition)
    }

    private fun seekPreviewBy(deltaMs: Int) {
        val player = previewPlayer

        if (player == null || !player.isReady()) {
            ToastUtil.show(this, R.string.audio_editor_no_playing)
            return
        }

        if (deltaMs < 0) {
            player.seekBackward()
        } else {
            player.seekForward()
        }
    }

    private fun markStartFromPlayback() {
        val player = previewPlayer

        if (player == null || !player.isReady() || !player.isPlaying()) {
            ToastUtil.show(this, R.string.audio_editor_no_playing)
            return
        }

        val position = player.currentPosition()
        val clipEnd = binding.audioEditorWave.getClipRightMilliseconds()

        if (position >= clipEnd) {
            ToastUtil.show(this, R.string.audio_editor_start_error)
            return
        }

        binding.audioEditorWave.setClipLeft(position, false)
        onClipStartChanged(position)
    }

    private fun markEndFromPlayback() {
        val player = previewPlayer

        if (player == null || !player.isReady() || !player.isPlaying()) {
            ToastUtil.show(this, R.string.audio_editor_no_playing)
            return
        }

        val position = player.currentPosition()
        val clipStart = binding.audioEditorWave.getClipLeftMilliseconds()

        if (position <= clipStart) {
            ToastUtil.show(this, R.string.audio_editor_end_error)
            return
        }

        binding.audioEditorWave.setClipRight(position)
        onClipEndChanged(position)
    }

    private fun requestSave() {
        if (binding.audioEditorStartTime.isFocused && !binding.audioEditorStartTime.commitInput()) {
            return
        }
        if (binding.audioEditorEndTime.isFocused && !binding.audioEditorEndTime.commitInput()) {
            return
        }

        val clipStart = binding.audioEditorWave.getClipLeftMilliseconds()
        val clipEnd = binding.audioEditorWave.getClipRightMilliseconds()
        val clipDuration = binding.audioEditorWave.getClipDuration()

        if (clipStart >= clipEnd || clipDuration <= MIN_CLIP_DURATION_MS) {
            showMessageDialog(
                createMessageDialogConfig(
                    title = getString(R.string.error),
                    message = getString(R.string.song_clip_error),
                    positiveText = getString(R.string.ok)
                )
            )
            return
        }

        val input = layoutInflater.inflate(
            R.layout.layout_edittext,
            null,
            false
        ) as AppCompatEditText

        input.apply {
            val defaultName = (track?.title ?: "Clip") + getString(R.string.audio_editor_extension)
            setText(defaultName)
            applyLengthFilter(MAX_FILE_NAME_LENGTH)
            applyThemeTo(this)
            Selection.selectAll(text)
            showKeyboardDelayed()
        }

        val config = materialDialogConfigFactory
            .createMaterialMessageDialogConfig(this)
            .apply {
                titleText = getString(R.string.save)
                customView = input
                positiveButtonText = getString(R.string.save).uppercase()
                negativeButtonText = getString(R.string.cancel).uppercase()
                positiveButtonClickListener = DialogInterface.OnClickListener { dialog, _ ->
                    val fileName = input.extractValidatedText(false)

                    if (fileName.isNullOrBlank()) {
                        ToastUtil.show(this@AudioEditorActivity, R.string.input_error)
                        return@OnClickListener
                    }

                    dialog.dismiss()
                    saveClip(
                        fileName = fileName,
                        startMs = clipStart,
                        endMs = clipEnd
                    )
                }
                onDismissListener = DialogInterface.OnDismissListener {
                    input.hideKeyboard()
                }
            }

        MessageDialog.show(this, config)
    }

    private fun saveClip(
        fileName: String,
        startMs: Int,
        endMs: Int
    ) {
        val sourcePath = waveformData?.sourcePath ?: return

        setEditorEnabled(false)

        lifecycleScope.launch {
            val result = audioTrimRepository.exportClip(
                sourcePath = sourcePath,
                displayNameWithoutExtension = fileName,
                range = AudioClipRange(
                    startMs = startMs,
                    endMs = endMs,
                    startFrame = binding.audioEditorWave.getStartFrame(),
                    endFrame = binding.audioEditorWave.getEndFrame()
                )
            )

            result.onSuccess { insertedId ->
                withContext(Dispatchers.IO) {
                    MediaStoreMusicImporter()
                        .queryMusicById(this@AudioEditorActivity, insertedId)
                        ?.let { scannedTrack ->
                            upsertScannedTracksUseCase(listOf(scannedTrack))
                        }
                }

                ToastUtil.show(this@AudioEditorActivity, R.string.audio_editor_succeed)
                finish()
            }.onFailure {
                setEditorEnabled(true)
                val messageRes = if (it is AudioTrimNameExistsException) {
                    R.string.name_exist
                } else {
                    R.string.audio_editor_failed
                }
                ToastUtil.show(this@AudioEditorActivity, messageRes)
            }
        }
    }

    private fun refreshZoomButtons() {
        binding.audioEditorZoomIn.isEnabled = binding.audioEditorWave.canZoomIn()
        binding.audioEditorZoomOut.isEnabled = binding.audioEditorWave.canZoomOut()
    }

    private fun updateLengthText() {
        binding.audioEditorLength.text = TimeEditText.formatTime(
            binding.audioEditorWave.getClipDuration()
        )
    }

    private fun setEditorEnabled(enabled: Boolean) {
        binding.audioEditorPrevious.isEnabled = enabled
        binding.audioEditorNext.isEnabled = enabled
        binding.audioEditorStart.isEnabled = enabled
        binding.audioEditorEnd.isEnabled = enabled
        binding.audioEditorWave.isEnabled = enabled
        binding.audioEditorPlay.isEnabled = enabled
        binding.audioEditorStartPlus.isEnabled = enabled
        binding.audioEditorStartMinus.isEnabled = enabled
        binding.audioEditorEndPlus.isEnabled = enabled
        binding.audioEditorEndMinus.isEnabled = enabled
        binding.audioEditorStartTime.isEnabled = enabled
        binding.audioEditorEndTime.isEnabled = enabled

        binding.audioEditorZoomIn.isEnabled = enabled && binding.audioEditorWave.canZoomIn()
        binding.audioEditorZoomOut.isEnabled = enabled && binding.audioEditorWave.canZoomOut()
    }

    companion object {
        private const val EXTRA_MUSIC = "music"

        private const val SMALL_STEP_MS = 10
        private const val LARGE_STEP_MS = 2_000
        private const val MIN_CLIP_DURATION_MS = 39
        private const val MAX_FILE_NAME_LENGTH = 120

        private const val REPEATED_SEEK_DELAY_MS = 120L

        private val SUPPORTED_EXTENSIONS = setOf(
            "aac",
            "m4a",
            "mp4",
            "3gp",
            "wav",
            "ogg",
            "oga",
            "flac",
            "mp3"
        )

        fun start(context: Context, music: Music) {
            val path = music.data
            val extension = path?.let { File(it).extension.lowercase() }.orEmpty()

            if (extension !in SUPPORTED_EXTENSIONS) {
                ToastUtil.show(
                    context,
                    context.getString(
                        R.string.format_not_support,
                        extension.ifBlank { "unknown" }
                    )
                )
                return
            }

            context.startActivityCompat(
                Intent(context, AudioEditorActivity::class.java)
                    .putExtra(EXTRA_MUSIC, music)
            )
        }
    }
}
