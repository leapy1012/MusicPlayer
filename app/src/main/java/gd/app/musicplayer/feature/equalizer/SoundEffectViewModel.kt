package gd.app.musicplayer.feature.equalizer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import gd.app.musicplayer.core.datastore.SoundEffectPreferences
import gd.app.musicplayer.core.datastore.SoundEffectSettings
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
            persistLoudnessEnabled(enabled)
        }
    }

    fun setLoudnessStrength(value: Float) {
        viewModelScope.launch {
            persistLoudnessStrength(value)
        }
    }

    fun setReverbIndex(index: Int) {
        viewModelScope.launch {
            persistReverbIndex(index)
        }
    }

    fun setBalanceEnabled(enabled: Boolean) {
        viewModelScope.launch {
            persistBalanceEnabled(enabled)
        }
    }

    fun setBalanceLeft(value: Float) {
        viewModelScope.launch {
            persistBalanceLeft(value)
        }
    }

    fun setBalanceRight(value: Float) {
        viewModelScope.launch {
            persistBalanceRight(value)
        }
    }

    suspend fun persistLoudnessEnabled(enabled: Boolean) {
        soundEffectPreferences.setLoudnessEnabled(enabled)
    }

    suspend fun persistLoudnessStrength(value: Float) {
        soundEffectPreferences.setLoudnessStrength(value)
    }

    suspend fun persistReverbIndex(index: Int) {
        soundEffectPreferences.setReverbIndex(index)
    }

    suspend fun persistBalanceEnabled(enabled: Boolean) {
        soundEffectPreferences.setBalanceEnabled(enabled)
    }

    suspend fun persistBalanceLeft(value: Float) {
        soundEffectPreferences.setBalanceLeft(value)
    }

    suspend fun persistBalanceRight(value: Float) {
        soundEffectPreferences.setBalanceRight(value)
    }
}
