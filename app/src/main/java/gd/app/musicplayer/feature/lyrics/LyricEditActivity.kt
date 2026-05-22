package gd.app.musicplayer.feature.lyrics

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.Editable
import android.text.Selection
import android.text.TextWatcher
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import androidx.appcompat.widget.Toolbar
import androidx.core.content.getSystemService
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.startActivityCompat
import gd.app.musicplayer.core.common.util.ToastUtil
import gd.app.musicplayer.core.designsystem.dialog.createMessageDialogConfig
import gd.app.musicplayer.core.designsystem.dialog.showMessageDialog
import gd.app.musicplayer.core.designsystem.theme.ThemePalette
import gd.app.musicplayer.core.designsystem.theme.ThemeViewBinder
import gd.app.musicplayer.core.designsystem.theme.headerTitleColor
import gd.app.musicplayer.core.designsystem.theme.itemPrimaryTextColor
import gd.app.musicplayer.databinding.ActivityLyricEditBinding
import gd.app.musicplayer.ui.common.base.BaseActivity
import gd.app.musicplayer.ui.common.base.setupEdgeToEdgeToolbar
import gd.app.musicplayer.util.LyricsLoader
import gd.app.musicplayer.util.TrackLyricsStore
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class LyricEditActivity : BaseActivity(), Toolbar.OnMenuItemClickListener {

    private lateinit var binding: ActivityLyricEditBinding

    private var clipboardLyrics: String? = null
    private var initialLyricsText = ""
    private var hasExistingLyrics = false
    private val lyricThemeBinder = ThemeViewBinder { palette, payload, view ->
        if (payload == "dialogEditText" && view is EditText) {
            view.setTextColor(palette.itemPrimaryTextColor)
            view.setHintTextColor(
                if (usesDarkForegroundPalette(palette)) 1291845632 else 1308622847
            )
            view.background = ColorDrawable(Color.TRANSPARENT)
            true
        } else {
            false
        }
    }

    private val trackId: Long
        get() = intent.getLongExtra(EXTRA_TRACK_ID, INVALID_TRACK_ID)

    private val title: String
        get() = intent.getStringExtra(EXTRA_TITLE).orEmpty()

    private val audioPath: String?
        get() = intent.getStringExtra(EXTRA_AUDIO_PATH)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (trackId == INVALID_TRACK_ID) {
            finish()
            return
        }

        binding = ActivityLyricEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupEdgeToEdgeToolbar(
            root = binding.root,
            statusBarView = binding.statusBarSpace,
            toolbar = binding.toolbar,
            bottomPaddingView = binding.root,
            titleRes = R.string.edit_lyric
        )
        binding.toolbar.inflateMenu(R.menu.menu_activity_lyric_edit)
        binding.toolbar.setOnMenuItemClickListener(this)

        binding.btnPaste.setOnClickListener {
            val pasteText = clipboardLyrics ?: return@setOnClickListener
            binding.lrcEditInput.setText(pasteText)
            binding.lrcEditInput.setSelection(binding.lrcEditInput.text?.length ?: 0)
            clipboardLyrics = null
            updateActionState()
        }

        binding.lrcEditInput.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) = Unit

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int
                ) {
                    updateActionState()
                }

                override fun afterTextChanged(s: Editable?) = Unit
            }
        )

        clipboardLyrics = loadClipboardLyrics()
        updateActionState()

        binding.lrcEditInput.post {
            binding.lrcEditInput.requestFocus()
        }
        applyLyricTheme()

        lifecycleScope.launch {
            val result = LyricsLoader.load(this@LyricEditActivity, trackId, audioPath)
            if (isFinishing || isDestroyed || binding.lrcEditInput.text?.isNotEmpty() == true) {
                return@launch
            }

            val loadedText = result.text.orEmpty()
            initialLyricsText = loadedText
            hasExistingLyrics = result.hasLyrics

            if (loadedText.isNotEmpty()) {
                binding.lrcEditInput.setText(loadedText)
                Selection.setSelection(binding.lrcEditInput.text ?: return@launch, 0)
            }

            updateActionState()
        }
    }

    override fun onResume() {
        super.onResume()
        applyLyricTheme()
        binding.lrcEditInput.requestFocus()
    }

    override fun onThemeChanged(palette: ThemePalette?) {
        super.onThemeChanged(palette)
        if (::binding.isInitialized && palette != null) {
            themeRegistry.apply(binding.root, palette, lyricThemeBinder)
        }
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_lyric_delete -> {
                confirmDeleteLyrics()
                true
            }

            R.id.menu_lyric_save -> {
                saveLyrics()
                true
            }

            else -> false
        }
    }

    override fun onBackPressed() {
        if (hasUnsavedChanges()) {
            confirmDiscardChanges()
        } else {
            super.onBackPressed()
        }
    }

    private fun updateActionState() {
        val editText = binding.lrcEditInput.text?.toString().orEmpty()
        val isEmpty = editText.isBlank()

        binding.btnPaste.visibility =
            if (isEmpty && clipboardLyrics != null) View.VISIBLE else View.GONE

        binding.toolbar.menu.findItem(R.id.menu_lyric_delete)?.isVisible = !isEmpty
        binding.toolbar.menu.findItem(R.id.menu_lyric_save)?.isVisible =
            !isEmpty || hasExistingLyrics
    }

    private fun hasUnsavedChanges(): Boolean {
        return binding.lrcEditInput.text?.toString().orEmpty() != initialLyricsText
    }

    private fun confirmDeleteLyrics() {
        showMessageDialog(
            createMessageDialogConfig(
                title = getString(R.string.delete),
                message = getString(R.string.lyric_edit_delete_msg),
                positiveText = getString(R.string.delete),
                negativeText = getString(R.string.cancel),
                positiveClickListener = { dialog, _ ->
                    dialog.dismiss()
                    deleteLyrics()
                }
            )
        )
    }

    private fun confirmDiscardChanges() {
        showMessageDialog(
            createMessageDialogConfig(
                title = getString(R.string.lyric_edit_back_title),
                message = getString(R.string.lyric_edit_back_msg),
                positiveText = getString(R.string.lyric_edit_back_title),
                negativeText = getString(R.string.cancel),
                positiveClickListener = { dialog, _ ->
                    dialog.dismiss()
                    finish()
                }
            )
        )
    }

    private fun saveLyrics() {
        val text = binding.lrcEditInput.text?.toString()?.trim().orEmpty()
        if (text.isBlank() && !hasExistingLyrics) {
            ToastUtil.show(this, R.string.equalizer_edit_input_error)
            return
        }

        lifecycleScope.launch {
            val success = withContext(Dispatchers.IO) {
                runCatching {
                    val store = TrackLyricsStore.from(this@LyricEditActivity)
                    if (text.isBlank()) {
                        store.clearTrackLyricData(trackId)
                    } else {
                        val file = File(filesDir, "lyrics/edited/track_${trackId}.lrc")
                        file.parentFile?.mkdirs()
                        file.writeText(text)
                        store.setTrackLyricPath(trackId, file.absolutePath)
                    }
                    true
                }.getOrElse { false }
            }

            if (!success) {
                ToastUtil.show(this@LyricEditActivity, R.string.save_lyric_failed)
                return@launch
            }

            setResult(RESULT_OK)
            ToastUtil.show(this@LyricEditActivity, R.string.audio_editor_succeed)
            finish()
        }
    }

    private fun deleteLyrics() {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                TrackLyricsStore.from(this@LyricEditActivity).clearTrackLyricData(trackId)
            }
            setResult(RESULT_OK)
            ToastUtil.show(this@LyricEditActivity, R.string.audio_editor_succeed)
            finish()
        }
    }

    private fun loadClipboardLyrics(): String? {
        val clipboard = getSystemService<ClipboardManager>() ?: return null
        val description = clipboard.primaryClipDescription ?: return null
        if (!description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) &&
            !description.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML)
        ) {
            return null
        }
        val clip = clipboard.primaryClip ?: return null
        val item = clip.getItemAt(0) ?: return null
        return item.coerceToText(this)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun applyLyricTheme() {
        if (::binding.isInitialized) {
            themeRegistry.apply(binding.root, themeEngine.currentTheme(), lyricThemeBinder)
        }
    }

    private fun usesDarkForegroundPalette(palette: ThemePalette): Boolean {
        return palette.headerTitleColor != Color.WHITE
    }

    companion object {
        private const val EXTRA_TRACK_ID = "track_id"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_AUDIO_PATH = "audio_path"
        private const val INVALID_TRACK_ID = -1L

        fun intent(
            context: Context,
            trackId: Long,
            title: String,
            audioPath: String?
        ): Intent {
            return Intent(context, LyricEditActivity::class.java).apply {
                putExtra(EXTRA_TRACK_ID, trackId)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_AUDIO_PATH, audioPath)
            }
        }

        fun start(
            context: Context,
            trackId: Long,
            title: String,
            audioPath: String?
        ) {
            context.startActivityCompat(intent(context, trackId, title, audioPath))
        }
    }
}
