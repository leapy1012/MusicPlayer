package gd.app.musicplayer.playback.service

import gd.app.musicplayer.core.common.AppForegroundTracker
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

internal fun MusicPlaybackService.observePreferences() {
    observeSettingPreferences()
    observeDesktopLyricPreference()
    observeStatusBarLyricPreference()
    observeAudioEffectPreferences()
}

internal fun MusicPlaybackService.observeCurrentTrackArtwork() {
    if (isArtworkControllerInitialized()) {
        artworkController.observe(serviceScope)
    }
}


internal fun MusicPlaybackService.observeCurrentTrackFavorite() {
    if (isFavoriteControllerInitialized()) {
        favoriteController.observe(serviceScope)
    }
}

internal fun MusicPlaybackService.observeSettingPreferences() {
    settingPreferencesDataStore.observeSettingPreferences()
        .onEach { preferences ->
            latestSettingPreferences = preferences
        }
        .launchIn(serviceScope)
}

internal fun MusicPlaybackService.observeDesktopLyricPreference() {
    desktopLyricPreferenceStore.desktopLyricPreference
        .onEach { preference ->
            latestDesktopLyricPreference = preference
            if (isDesktopLyricsControllerInitialized()) {
                desktopLyricsController.renderPreference(preference)
            }
            updateNotification(force = true)
        }
        .launchIn(serviceScope)

    AppForegroundTracker.isForeground
        .onEach { isForeground ->
            if (isDesktopLyricsControllerInitialized()) {
                desktopLyricsController.renderAppForeground(isForeground)
            }
        }
        .launchIn(serviceScope)

    playbackRuntimeStateStore.state
        .onEach { state ->
            if (isDesktopLyricsControllerInitialized()) {
                desktopLyricsController.renderPlaybackState(state)
            }
        }
        .launchIn(serviceScope)
}

internal fun MusicPlaybackService.observeStatusBarLyricPreference() {
    statusBarLyricPreferenceStore.preference
        .onEach { preference ->
            if (isStatusBarLyricsControllerInitialized()) {
                statusBarLyricsController.renderPreference(preference)
            }
        }
        .launchIn(serviceScope)

    playbackRuntimeStateStore.state
        .onEach { state ->
            if (isStatusBarLyricsControllerInitialized()) {
                statusBarLyricsController.renderPlaybackState(state)
            }
        }
        .launchIn(serviceScope)
}

internal fun MusicPlaybackService.observeAudioEffectPreferences() {
    combine(
        soundEffectPreferences.equalizerPreference,
        soundEffectPreferences.soundEffectSettings
    ) { equalizerPreference, soundEffectSettings ->
        equalizerPreference to soundEffectSettings
    }
        .distinctUntilChanged()
        .onEach {
            if (isPlayerInitialized()) {
                applyAudioEffectsFromPreferences()
            }
        }
        .launchIn(serviceScope)
}


