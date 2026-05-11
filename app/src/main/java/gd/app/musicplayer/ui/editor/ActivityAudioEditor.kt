package gd.app.musicplayer.ui.editor

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatEditText
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.extractValidatedText
import gd.app.musicplayer.core.common.extension.parcelable
import gd.app.musicplayer.core.common.extension.startActivityCompat
import gd.app.musicplayer.data.local.mediastore.MediaStoreMusicImporter
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.domain.usecase.scan.UpsertScannedTracksUseCase
import gd.app.musicplayer.databinding.ActivityAudioEditorBinding
import gd.app.musicplayer.ui.editor.waveform.SoundWaveData
import gd.app.musicplayer.ui.editor.waveform.SoundWaveView
import gd.app.musicplayer.ui.editor.waveform.TimeEditText
import gd.app.musicplayer.ui.common.base.BaseActivity
import gd.app.musicplayer.ui.common.base.setupEdgeToEdgeToolbar
import gd.app.musicplayer.core.common.util.ToastUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min

@AndroidEntryPoint
class ActivityAudioEditor : BaseActivity(),
    SoundWaveView.OnClipChangedListener,
    TimeEditText.OnInputTimeChangedListener {

    private lateinit var binding: ActivityAudioEditorBinding
    private var track: Music? = null
    private var waveformData: SoundWaveData? = null
    private var mediaPlayer: MediaPlayer? = null
    private var playerPrepared = false

    @Inject lateinit var upsertScannedTracksUseCase: UpsertScannedTracksUseCase

    private val uiHandler = Handler(Looper.getMainLooper())
    private val progressUpdater = object : Runnable {
        override fun run() {
            val player = mediaPlayer ?: return
            if (!playerPrepared || !player.isPlaying) return

            val position = player.currentPosition
            if (position >= binding.audioEditorWave.getClipRightMilliseconds()) {
                pausePreview(resetToClipStart = true)
                return
            }
            binding.audioEditorWave.setProgress(position)
            uiHandler.postDelayed(this, 40L)
        }
    }

    private val repeatedSeekRunnable = object : Runnable {
        var deltaMs: Int = 0
        override fun run() {
            if (deltaMs == 0) return
            seekPreviewBy(deltaMs)
            uiHandler.postDelayed(this, 120L)
        }
    }

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

        setupEdgeToEdgeToolbar(
            root = binding.root,
            statusBarView = binding.statusBarSpace,
            toolbar = binding.toolbar,
            bottomPaddingView = binding.bottomControl as View
        )
        binding.toolbar.title = track?.title ?: getString(R.string.audio_editor_title)

        bindViews()
        if (savedInstanceState == null) {
            loadWaveform()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_activity_audio_editor, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.menu_save) {
            stopPreview()
            if (binding.audioEditorPlay.isEnabled) {
                requestSave()
            }
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onDestroy() {
        stopPreview()
        releasePlayer()
        super.onDestroy()
    }

    override fun onInputTimeChanged(timeEditText: TimeEditText, rawText: String, timeMs: Int) {
        if (timeEditText === binding.audioEditorStartTime) {
            val adjusted = min(timeMs, binding.audioEditorWave.getClipRightMilliseconds())
            if (adjusted != timeMs) {
                binding.audioEditorStartTime.setText(TimeEditText.formatTime(adjusted))
            }
            binding.audioEditorWave.setClipLeft(adjusted, false)
        } else if (timeEditText === binding.audioEditorEndTime) {
            val fallback = if (rawText.isEmpty()) {
                binding.audioEditorWave.getClipLeftMilliseconds()
            } else {
                max(timeMs, binding.audioEditorWave.getClipLeftMilliseconds())
            }
            if (fallback != timeMs) {
                binding.audioEditorEndTime.setText(TimeEditText.formatTime(fallback))
            }
            binding.audioEditorWave.setClipRight(fallback)
        }
        updateLengthText()
    }

    override fun onClipEndChanged(timeMs: Int) {
        val safeTime = timeMs.coerceAtLeast(0)
        binding.audioEditorEndTime.setText(TimeEditText.formatTime(safeTime))
        updateLengthText()
    }

    override fun onClipStartChanged(timeMs: Int) {
        val safeTime = timeMs.coerceAtLeast(0)
        binding.audioEditorStartTime.setText(TimeEditText.formatTime(safeTime))
        updateLengthText()
    }

    override fun onSeekRequested(timeMs: Int) {
        val left = binding.audioEditorWave.getClipLeftMilliseconds()
        val right = binding.audioEditorWave.getClipRightMilliseconds()
        ensurePlayer()
        when {
            timeMs < left -> previewTo(left)
            timeMs < right -> previewTo(timeMs)
            else -> previewTo(right)
        }
    }

    private fun bindViews() {
        binding.audioEditorWave.setOnClipChangedListener(this)
        binding.audioEditorStartTime.setOnInputTimeChangedListener(this)
        binding.audioEditorEndTime.setOnInputTimeChangedListener(this)

        binding.audioEditorPlay.setOnClickListener { togglePreview() }
        binding.audioEditorZoomIn.setOnClickListener {
            binding.audioEditorWave.zoomIn()
            refreshZoomButtons()
        }
        binding.audioEditorZoomOut.setOnClickListener {
            binding.audioEditorWave.zoomOut()
            refreshZoomButtons()
        }
        binding.audioEditorStart.setOnClickListener { markStartFromPlayback() }
        binding.audioEditorEnd.setOnClickListener { markEndFromPlayback() }
        binding.audioEditorStartMinus.setOnClickListener {
            binding.audioEditorWave.setClipLeft(max(0, binding.audioEditorWave.getClipLeftMilliseconds() - 10), false)
            onClipStartChanged(binding.audioEditorWave.getClipLeftMilliseconds())
        }
        binding.audioEditorStartPlus.setOnClickListener {
            binding.audioEditorWave.setClipLeft(
                min(
                    binding.audioEditorWave.getClipRightMilliseconds(),
                    binding.audioEditorWave.getClipLeftMilliseconds() + 10
                ),
                false
            )
            onClipStartChanged(binding.audioEditorWave.getClipLeftMilliseconds())
        }
        binding.audioEditorEndMinus.setOnClickListener {
            binding.audioEditorWave.setClipRight(
                max(
                    binding.audioEditorWave.getClipLeftMilliseconds(),
                    binding.audioEditorWave.getClipRightMilliseconds() - 10
                )
            )
            onClipEndChanged(binding.audioEditorWave.getClipRightMilliseconds())
        }
        binding.audioEditorEndPlus.setOnClickListener {
            binding.audioEditorWave.setClipRight(
                min(
                    binding.audioEditorWave.getDuration(),
                    binding.audioEditorWave.getClipRightMilliseconds() + 10
                )
            )
            onClipEndChanged(binding.audioEditorWave.getClipRightMilliseconds())
        }

        bindRepeatedSeek(binding.audioEditorPrevious, -2_000)
        bindRepeatedSeek(binding.audioEditorNext, 2_000)
        setEditorEnabled(false)
    }

    private fun bindRepeatedSeek(view: View, deltaMs: Int) {
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    repeatedSeekRunnable.deltaMs = deltaMs
                    seekPreviewBy(deltaMs)
                    uiHandler.postDelayed(repeatedSeekRunnable, 200L)
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    stopRepeatedSeek()
                    true
                }

                else -> false
            }
        }
    }

    private fun ensurePlayer() {
        if (mediaPlayer != null) return
        val path = waveformData?.sourcePath ?: return
        mediaPlayer = MediaPlayer().apply {
            setDataSource(path)
            setOnPreparedListener {
                playerPrepared = true
                seekTo(binding.audioEditorWave.getClipLeftMilliseconds())
            }
            setOnCompletionListener {
                pausePreview(resetToClipStart = true)
            }
            prepare()
            playerPrepared = true
        }
    }

    private fun loadWaveform() {
        val path = track?.data ?: return
        setEditorEnabled(false)
        lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) { SoundWaveData.load(path) }
            if (loaded == null) {
                ToastUtil.show(this@ActivityAudioEditor, R.string.audio_editor_error)
                finish()
                return@launch
            }

            waveformData = loaded
            binding.audioEditorInfo.text = loaded.infoText()
            binding.audioEditorWave.setSoundFile(loaded)
            binding.audioEditorStartTime.setMinTime(0)
            binding.audioEditorStartTime.setMaxTime(loaded.durationMs)
            binding.audioEditorEndTime.setMinTime(0)
            binding.audioEditorEndTime.setMaxTime(loaded.durationMs)
            binding.audioEditorStartTime.setText(TimeEditText.formatTime(0))
            binding.audioEditorEndTime.setText(TimeEditText.formatTime(loaded.durationMs))
            updateLengthText()
            refreshZoomButtons()
            ensurePlayer()
            setEditorEnabled(true)
        }
    }

    private fun markEndFromPlayback() {
        val player = mediaPlayer
        if (player == null || !playerPrepared || !player.isPlaying) {
            ToastUtil.show(this, R.string.audio_editor_no_playing)
            return
        }
        val position = player.currentPosition
        if (position <= binding.audioEditorWave.getClipLeftMilliseconds()) {
            ToastUtil.show(this, R.string.audio_editor_end_error)
            return
        }
        binding.audioEditorWave.setClipRight(position)
        onClipEndChanged(position)
    }

    private fun markStartFromPlayback() {
        val player = mediaPlayer
        if (player == null || !playerPrepared || !player.isPlaying) {
            ToastUtil.show(this, R.string.audio_editor_no_playing)
            return
        }
        val position = player.currentPosition
        if (position >= binding.audioEditorWave.getClipRightMilliseconds()) {
            ToastUtil.show(this, R.string.audio_editor_start_error)
            return
        }
        binding.audioEditorWave.setClipLeft(position, false)
        onClipStartChanged(position)
    }

    private fun pausePreview(resetToClipStart: Boolean) {
        val player = mediaPlayer ?: return
        if (player.isPlaying) {
            player.pause()
        }
        uiHandler.removeCallbacks(progressUpdater)
        if (resetToClipStart && playerPrepared) {
            val startMs = binding.audioEditorWave.getClipLeftMilliseconds()
            player.seekTo(startMs)
            binding.audioEditorWave.setProgress(startMs)
        }
        binding.audioEditorPlay.isSelected = false
        binding.audioEditorWave.setSeek(true)
    }

    private fun previewTo(positionMs: Int) {
        ensurePlayer()
        val player = mediaPlayer ?: return
        if (!playerPrepared) return
        player.seekTo(positionMs.coerceIn(0, binding.audioEditorWave.getDuration()))
        binding.audioEditorWave.setProgress(player.currentPosition)
    }

    private fun refreshZoomButtons() {
        binding.audioEditorZoomIn.isEnabled = binding.audioEditorWave.canZoomIn()
        binding.audioEditorZoomOut.isEnabled = binding.audioEditorWave.canZoomOut()
    }

    private fun releasePlayer() {
        playerPrepared = false
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun requestSave() {
        val clipStart = binding.audioEditorWave.getClipLeftMilliseconds()
        val clipEnd = binding.audioEditorWave.getClipRightMilliseconds()
        val clipDuration = binding.audioEditorWave.getClipDuration()
        if (clipStart >= clipEnd || clipDuration <= 39) {
            AlertDialog.Builder(this)
                .setTitle(R.string.error)
                .setMessage(R.string.song_clip_error)
                .setPositiveButton(R.string.ok, null)
                .show()
            return
        }

        if (binding.audioEditorStartTime.isFocused) binding.audioEditorStartTime.commitInput()
        if (binding.audioEditorEndTime.isFocused) binding.audioEditorEndTime.commitInput()

        val input = AppCompatEditText(this).apply {
            val defaultName = (track?.title ?: "Clip") + getString(R.string.audio_editor_extension)
            setText(defaultName)
            setSelection(text?.length ?: 0)
            setSingleLine()
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.save)
            .setView(input)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel, null)
            .create()
            .also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val fileName = input.extractValidatedText(false)
                        if (fileName.isNullOrBlank()) {
                            ToastUtil.show(this, R.string.input_error)
                            return@setOnClickListener
                        }
                        dialog.dismiss()
                        saveClip(fileName, clipStart, clipEnd)
                    }
                }
            }
            .show()
    }

    private fun saveClip(fileName: String, startMs: Int, endMs: Int) {
        val sourcePath = waveformData?.sourcePath ?: return
        setEditorEnabled(false)
        lifecycleScope.launch {
            val insertedId = withContext(Dispatchers.IO) {
                AudioTrimStore.exportClip(
                    context = this@ActivityAudioEditor,
                    sourcePath = sourcePath,
                    displayNameWithoutExtension = fileName,
                    startMs = startMs,
                    endMs = endMs
                )
            }

            if (insertedId != null) {
                withContext(Dispatchers.IO) {
                    MediaStoreMusicImporter().queryMusicById(this@ActivityAudioEditor, insertedId)
                        ?.let { upsertScannedTracksUseCase(listOf(it)) }
                }
                ToastUtil.show(this@ActivityAudioEditor, R.string.audio_editor_succeed)
                finish()
            } else {
                setEditorEnabled(true)
                ToastUtil.show(this@ActivityAudioEditor, R.string.audio_editor_failed)
            }
        }
    }

    private fun seekPreviewBy(deltaMs: Int) {
        val player = mediaPlayer
        if (player == null || !playerPrepared) {
            ToastUtil.show(this, R.string.audio_editor_no_playing)
            stopRepeatedSeek()
            return
        }

        val target = (player.currentPosition + deltaMs).coerceIn(
            binding.audioEditorWave.getClipLeftMilliseconds(),
            binding.audioEditorWave.getClipRightMilliseconds()
        )
        player.seekTo(target)
        binding.audioEditorWave.setProgress(target)
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
        binding.audioEditorZoomIn.isEnabled = enabled && binding.audioEditorWave.canZoomIn()
        binding.audioEditorZoomOut.isEnabled = enabled && binding.audioEditorWave.canZoomOut()
        binding.audioEditorStartTime.isEnabled = enabled
        binding.audioEditorEndTime.isEnabled = enabled
    }

    private fun stopPreview() {
        stopRepeatedSeek()
        uiHandler.removeCallbacks(progressUpdater)
        pausePreview(resetToClipStart = false)
    }

    private fun stopRepeatedSeek() {
        repeatedSeekRunnable.deltaMs = 0
        uiHandler.removeCallbacks(repeatedSeekRunnable)
    }

    private fun togglePreview() {
        ensurePlayer()
        val player = mediaPlayer ?: return
        if (!playerPrepared) return

        if (player.isPlaying) {
            pausePreview(resetToClipStart = false)
            return
        }

        val startMs = binding.audioEditorWave.getClipLeftMilliseconds()
        val endMs = binding.audioEditorWave.getClipRightMilliseconds()
        val current = player.currentPosition
        if (current < startMs || current >= endMs) {
            player.seekTo(startMs)
            binding.audioEditorWave.setProgress(startMs)
        }
        player.start()
        binding.audioEditorPlay.isSelected = true
        binding.audioEditorWave.setSeek(true)
        uiHandler.removeCallbacks(progressUpdater)
        uiHandler.post(progressUpdater)
    }

    private fun updateLengthText() {
        binding.audioEditorLength.text = TimeEditText.formatTime(binding.audioEditorWave.getClipDuration())
    }

    companion object {
        private const val EXTRA_MUSIC = "music"
        private val SUPPORTED_EXTENSIONS = setOf(
            "aac", "m4a", "mp4", "3gp", "wav", "ogg", "oga", "flac", "mp3"
        )

        fun start(context: Context, music: Music) {
            val path = music.data
            val extension = path?.let { File(it).extension.lowercase() }.orEmpty()
            if (extension !in SUPPORTED_EXTENSIONS) {
                ToastUtil.show(
                    context,
                    context.getString(R.string.format_not_support, extension.ifBlank { "unknown" })
                )
                return
            }
            context.startActivityCompat(
                Intent(context, ActivityAudioEditor::class.java).putExtra(EXTRA_MUSIC, music)
            )
        }
    }
}
