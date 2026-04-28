package gd.app.musicplayer.ui.feature.selection

import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import gd.app.musicplayer.R
import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.ui.shell.MainActivity
import gd.app.musicplayer.core.util.ToastUtil
import java.io.File

object MusicShareSupport {

    private const val MAX_SHARE_COUNT = 300

    fun share(context: Context, songs: List<Music>) {
        if (songs.isEmpty()) {
            ToastUtil.show(context, context.getString(R.string.select_musics_empty))
            return
        }
        if (songs.size > MAX_SHARE_COUNT) {
            ToastUtil.show(context, context.getString(R.string.share_too_much_files))
            return
        }

        val uris = songs.mapNotNull { toShareUri(context, it) }
        if (uris.isEmpty()) {
            ToastUtil.show(context, context.getString(R.string.music_empty))
            return
        }

        val shareIntent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = "*/*"
                putExtra(Intent.EXTRA_STREAM, uris.first())
                clipData = ClipData.newRawUri(null, uris.first())
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                clipData = buildClipData(uris)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }

        val chooserIntent = Intent.createChooser(
            shareIntent,
            context.getString(R.string.dlg_share_music)
        ).apply {
            putExtra(
                Intent.EXTRA_EXCLUDE_COMPONENTS,
                arrayOf(ComponentName(context, MainActivity::class.java))
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(chooserIntent)
    }

    private fun buildClipData(uris: List<Uri>): ClipData {
        val firstUri = uris.first()
        val clipData = ClipData.newRawUri(null, firstUri)
        uris.drop(1).forEach { uri ->
            clipData.addItem(ClipData.Item(uri))
        }
        return clipData
    }

    private fun toShareUri(context: Context, music: Music): Uri? {
        val path = music.data?.takeIf { it.isNotBlank() } ?: return null
        val file = File(path)
        if (!file.exists()) return null

        return FileProvider.getUriForFile(
            context.applicationContext,
            "${context.packageName}.fileprovider",
            file
        )
    }
}
