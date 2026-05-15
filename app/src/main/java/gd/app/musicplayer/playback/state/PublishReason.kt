package gd.app.musicplayer.playback.state

enum class PublishReason {
    ProgressTick,
    PlayerEvent,
    QueueChanged,
    Restore,
    UserAction,
    FavoriteChanged,
    ArtworkChanged,
    NotificationDismissed,
    Shutdown
}