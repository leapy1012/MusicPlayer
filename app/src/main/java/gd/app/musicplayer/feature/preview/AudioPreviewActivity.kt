package gd.app.musicplayer.feature.preview

import android.content.DialogInterface
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.core.graphics.drawable.toDrawable
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.extractExternalAudioUris
import gd.app.musicplayer.core.common.extension.isExternalAudioIntent
import gd.app.musicplayer.core.common.util.ToastUtil
import gd.app.musicplayer.core.designsystem.theme.ThemeManager
import gd.app.musicplayer.ui.common.base.BaseActivity
import gd.app.musicplayer.ui.shell.MainActivity
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Transparent host for [AudioPreview]. Receives external VIEW/SEND audio intents
 * so opening a file from a file manager does not launch the full player shell.
 */
@AndroidEntryPoint
class AudioPreviewActivity : BaseActivity() {

    @Inject lateinit var themeManager: ThemeManager

    private var preview: AudioPreview? = null
    private var previewUri: Uri? = null
    private var replacingPreview = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)

        if (!intent.isExternalAudioIntent()) {
            finishWithoutTransition()
            return
        }

        val uri = intent.extractExternalAudioUris().firstOrNull()
        if (uri == null) {
            ToastUtil.show(this, R.string.failed)
            finishWithoutTransition()
            return
        }

        previewUri = uri
        openPreview(uri)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        val uri = intent.extractExternalAudioUris().firstOrNull() ?: return
        if (uri == previewUri && preview?.isShowing == true) return

        previewUri = uri
        replacingPreview = true
        preview?.dismissQuietly()
        preview = null
        replacingPreview = false
        openPreview(uri)
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(0, 0)
    }

    private fun openPreview(uri: Uri) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                themeManager.warmUp()
            }
            if (isFinishing) return@launch
            showPreview(uri)
        }
    }

    private fun showPreview(uri: Uri) {
        val palette = themeEngine.currentTheme()

        val config = AudioPreview.Config(
            audioUri = uri,
            themePalette = palette,
            onOpenInPlayer = { current -> openInPlayer(current) }
        ).apply {
            backgroundDrawable = Color.TRANSPARENT.toDrawable()
            cornerRadii = null
            onDismissListener = DialogInterface.OnDismissListener {
                preview = null
                if (!replacingPreview && !isFinishing) {
                    finishWithoutTransition()
                }
            }
        }

        preview = AudioPreview(this, config).also { it.show() }
    }

    private fun openInPlayer(uri: Uri) {
        val mime = intent.type?.takeIf { it.startsWith("audio/") } ?: "audio/*"
        val playIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            clipData = intent.clipData
            intent.extras?.let { putExtras(it) }
        }
        MainActivity.start(this, playIntent)
    }

    private fun finishWithoutTransition() {
        finish()
        overridePendingTransition(0, 0)
    }
}
