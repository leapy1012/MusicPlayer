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
    withArtworkController {
        artworkController.observe(serviceScope)
    }
}


internal fun MusicPlaybackService.observeCurrentTrackFavorite() {
    withFavoriteController {
        favoriteController.observe(serviceScope)
    }
}

internal fun MusicPlaybackService.observeSettingPreferences() {
    settingPreferencesDataStore.observeSettingPreferences()
        .onEach { preferences ->
            runtimeCacheState.latestSettingPreferences = preferences
        }
        .launchIn(serviceScope)
}

internal fun MusicPlaybackService.observeDesktopLyricPreference() {
    desktopLyricPreferenceStore.desktopLyricPreference
        .onEach { preference ->
            runtimeCacheState.latestDesktopLyricPreference = preference
            withDesktopLyricsController {
                desktopLyricsController.renderPreference(preference)
            }
            updateNotification(force = true)
        }
        .launchIn(serviceScope)

    AppForegroundTracker.isForeground
        .onEach { isForeground ->
            withDesktopLyricsController {
                desktopLyricsController.renderAppForeground(isForeground)
            }
        }
        .launchIn(serviceScope)

    playbackRuntimeStateStore.state
        .onEach { state ->
            withDesktopLyricsController {
                desktopLyricsController.renderPlaybackState(state)
            }
        }
        .launchIn(serviceScope)
}

internal fun MusicPlaybackService.observeStatusBarLyricPreference() {
    statusBarLyricPreferenceStore.preference
        .onEach { preference ->
            withStatusBarLyricsController {
                statusBarLyricsController.renderPreference(preference)
            }
        }
        .launchIn(serviceScope)

    playbackRuntimeStateStore.state
        .onEach { state ->
            withStatusBarLyricsController {
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
            withPlayer {
                applyAudioEffectsFromPreferences()
            }
        }
        .launchIn(serviceScope)
}


