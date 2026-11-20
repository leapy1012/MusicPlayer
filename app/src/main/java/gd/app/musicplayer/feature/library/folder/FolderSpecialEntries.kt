package gd.app.musicplayer.feature.library.folder

import android.content.Context
import gd.app.musicplayer.R
import gd.app.musicplayer.domain.model.MusicSet

internal const val HIDDEN_FOLDERS_ID = MusicSet.HIDDEN_FOLDERS

internal const val MUSIC_DIRECTORY_ID = MusicSet.MUSIC_DIRECTORY

internal fun hiddenFoldersEntry(context: Context): MusicSet.Folder =
    MusicSet.Folder(
        id = HIDDEN_FOLDERS_ID,
        name = context.getString(R.string.hidden_folders),
        folderPath = context.getString(R.string.display_hidden_folders),
        musicCount = 0,
        albumArt = null,
        date = 0L
    )

/** Original `w7.w.d`: `new MusicSet(-16, R.string.music_directory, 0)`. */
internal fun musicDirectoryEntry(context: Context): MusicSet.Folder =
    MusicSet.Folder(
        id = MUSIC_DIRECTORY_ID,
        name = context.getString(R.string.music_directory),
        folderPath = context.getString(R.string.music_directory),
        musicCount = 0,
        albumArt = null,
        date = 0L
    )

internal fun MusicSet.isHiddenFoldersEntry(): Boolean =
    this is MusicSet.Folder && id == HIDDEN_FOLDERS_ID

internal fun MusicSet.isMusicDirectoryEntry(): Boolean =
    this is MusicSet.Folder && id == MUSIC_DIRECTORY_ID

/**
 * Original `l5.d.c.f` / `onLongClick`: both `-14` and `-16` rows hide the artist/menu/des views
 * and are excluded from the long-press selection flow.
 */
internal fun MusicSet.isFolderSpecialEntry(): Boolean =
    isHiddenFoldersEntry() || isMusicDirectoryEntry()
