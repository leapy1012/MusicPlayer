package gd.app.musicplayer.playback.service


internal fun MusicPlaybackService.updateNotification(force: Boolean = false) {
    if (isStateUpdateCoordinatorInitialized()) {
        stateUpdateCoordinator.updateNotification(force = force)
        return
    }

    if (isStateOrchestratorInitialized()) {
        stateOrchestrator.updateNotification(force = force)
    }
}


