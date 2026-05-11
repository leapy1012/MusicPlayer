package gd.app.musicplayer.ui.library.folder

import android.content.Context
import gd.app.musicplayer.R
import gd.app.musicplayer.domain.model.MusicSet

internal const val HIDDEN_FOLDERS_ID = -14L

internal fun hiddenFoldersEntry(context: Context): MusicSet.Folder =
    MusicSet.Folder(
        id = HIDDEN_FOLDERS_ID,
        name = context.getString(R.string.hidden_folders),
        folderPath = context.getString(R.string.display_hidden_folders),
        musicCount = 0,
        albumArt = null,
        date = 0L
    )

internal fun MusicSet.isHiddenFoldersEntry(): Boolean =
    this is MusicSet.Folder && id == HIDDEN_FOLDERS_ID
