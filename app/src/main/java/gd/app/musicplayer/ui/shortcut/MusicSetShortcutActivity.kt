package gd.app.musicplayer.ui.shortcut

import android.os.Bundle
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.util.ToastUtil
import gd.app.musicplayer.ui.common.base.BaseActivity
import gd.app.musicplayer.ui.library.albums.AlbumMusicActivity
import gd.app.musicplayer.ui.shell.MainActivity

@AndroidEntryPoint
class MusicSetShortcutActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleShortcut()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShortcut()
    }

    private fun handleShortcut() {
        val musicSet = MusicSetShortcutHelper.extractMusicSet(intent)
        if (musicSet == null) {
            ToastUtil.show(this, R.string.failed)
            MainActivity.start(this)
            finish()
            return
        }

        AlbumMusicActivity.start(this, musicSet)
        finish()
    }
}
