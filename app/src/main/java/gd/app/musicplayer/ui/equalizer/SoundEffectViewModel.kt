package gd.app.musicplayer.ui.equalizer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import gd.app.musicplayer.data.local.preference.SoundEffectPreferences
import gd.app.musicplayer.data.local.preference.SoundEffectSettings
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SoundEffectViewModel @Inject constructor(
    private val soundEffectPreferences: SoundEffectPreferences
) : ViewModel() {

    val settings: StateFlow<SoundEffectSettings> =
        soundEffectPreferences.soundEffectSettings.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SoundEffectSettings()
        )

    fun setLoudnessEnabled(enabled: Boolean) {
        viewModelScope.launch {
            soundEffectPreferences.setLoudnessEnabled(enabled)
        }
    }

    fun setLoudnessStrength(value: Float) {
        viewModelScope.launch {
            soundEffectPreferences.setLoudnessStrength(value)
        }
    }

    fun setReverbIndex(index: Int) {
        viewModelScope.launch {
            soundEffectPreferences.setReverbIndex(index)
        }
    }

    fun setBalanceEnabled(enabled: Boolean) {
        viewModelScope.launch {
            soundEffectPreferences.setBalanceEnabled(enabled)
        }
    }

    fun setBalanceLeft(value: Float) {
        viewModelScope.launch {
            soundEffectPreferences.setBalanceLeft(value)
        }
    }

    fun setBalanceRight(value: Float) {
        viewModelScope.launch {
            soundEffectPreferences.setBalanceRight(value)
        }
    }
}