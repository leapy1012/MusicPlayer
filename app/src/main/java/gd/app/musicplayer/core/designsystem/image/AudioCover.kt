package gd.app.musicplayer.core.designsystem.image

/**
 * Glide model for embedded album art extracted from an audio file path or content URI.
 * Avoids MediaStore `audio_albums` / albumart open paths that crash on some OEM providers
 * (`SQLiteException: no such column: _data`).
 */
data class AudioCover(val source: String)
