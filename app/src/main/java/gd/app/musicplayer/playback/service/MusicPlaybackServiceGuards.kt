package gd.app.musicplayer.playback.service

internal inline fun MusicPlaybackService.withNotificationController(
    block: () -> Unit
) {
    notificationControllerOrNull()?.let { block() }
}

internal inline fun MusicPlaybackService.withNotificationSessionBridge(
    block: () -> Unit
) {
    notificationSessionBridgeOrNull()?.let { block() }
}

internal inline fun MusicPlaybackService.withArtworkController(
    block: () -> Unit
) {
    artworkControllerOrNull()?.let { block() }
}

internal inline fun MusicPlaybackService.withFavoriteController(
    block: () -> Unit
) {
    favoriteControllerOrNull()?.let { block() }
}

internal inline fun MusicPlaybackService.withDesktopLyricsController(
    block: () -> Unit
) {
    if (desktopLyricsControllerOrNull() == null) return
    block()
}

internal inline fun MusicPlaybackService.withStatusBarLyricsController(
    block: () -> Unit
) {
    if (statusBarLyricsControllerOrNull() == null) return
    block()
}

internal inline fun MusicPlaybackService.withProgressTicker(
    block: () -> Unit
) {
    progressTickerOrNull()?.let { block() }
}

internal inline fun MusicPlaybackService.withPlayer(
    block: () -> Unit
) {
    playerOrNull()?.let { block() }
}
