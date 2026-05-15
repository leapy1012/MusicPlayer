package gd.app.musicplayer.playback.restore

enum class RestoreStatus {
    NotStarted,
    Restoring,
    Restored,
    Empty,
    Failed;

    val isFinished: Boolean
        get() = this == Restored || this == Empty || this == Failed
}