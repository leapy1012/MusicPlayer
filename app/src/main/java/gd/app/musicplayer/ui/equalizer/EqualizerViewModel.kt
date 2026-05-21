package gd.app.musicplayer.ui.equalizer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import gd.app.musicplayer.data.local.preference.EqualizerPreference
import gd.app.musicplayer.data.local.preference.SoundEffectPreferences
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class EqualizerViewModel @Inject constructor(
    private val soundEffectPreferences: SoundEffectPreferences
) : ViewModel() {

    val settings: StateFlow<EqualizerPreference> =
        soundEffectPreferences.equalizerPreference.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = EqualizerPreference()
        )

    fun setEqualizerEnabled(enabled: Boolean) {
        viewModelScope.launch {
            persistEqualizerEnabled(enabled)
        }
    }

    fun setBandMode(bandMode: Int) {
        viewModelScope.launch {
            persistBandMode(bandMode)
        }
    }

    fun setSelectedEffectId(effectId: Int) {
        viewModelScope.launch {
            persistSelectedEffectId(effectId)
        }
    }

    fun setBassEnabled(enabled: Boolean) {
        viewModelScope.launch {
            persistBassEnabled(enabled)
        }
    }

    fun setBassProgress(progress: Float) {
        viewModelScope.launch {
            persistBassProgress(progress)
        }
    }

    fun setBassPresetId(presetId: Int) {
        viewModelScope.launch {
            soundEffectPreferences.setBassPresetId(presetId)
        }
    }

    fun setVirtualizerEnabled(enabled: Boolean) {
        viewModelScope.launch {
            persistVirtualizerEnabled(enabled)
        }
    }

    fun setVirtualizerProgress(progress: Float) {
        viewModelScope.launch {
            persistVirtualizerProgress(progress)
        }
    }

    fun setVirtualizerPresetId(presetId: Int) {
        viewModelScope.launch {
            soundEffectPreferences.setVirtualizerPresetId(presetId)
        }
    }

    fun setGroupSoundEffectEnabled(enabled: Boolean) {
        viewModelScope.launch {
            soundEffectPreferences.setGroupSoundEffectEnabled(enabled)
        }
    }

    fun setGroupSoundEffectIndex(index: Int) {
        viewModelScope.launch {
            soundEffectPreferences.setGroupSoundEffectIndex(index)
        }
    }

    suspend fun persistEqualizerEnabled(enabled: Boolean) {
        soundEffectPreferences.setEqualizerEnabled(enabled)
    }

    suspend fun persistBandMode(bandMode: Int) {
        soundEffectPreferences.setEqualizerBandMode(bandMode)
    }

    suspend fun persistSelectedEffectId(effectId: Int) {
        soundEffectPreferences.setLastEffectId(
            settings.value.bandMode,
            effectId
        )
    }

    suspend fun persistBassEnabled(enabled: Boolean) {
        soundEffectPreferences.setBassEnabled(enabled)
    }

    suspend fun persistBassProgress(progress: Float) {
        soundEffectPreferences.setBassProgress(progress)
    }

    suspend fun persistVirtualizerEnabled(enabled: Boolean) {
        soundEffectPreferences.setVirtualizerEnabled(enabled)
    }

    suspend fun persistVirtualizerProgress(progress: Float) {
        soundEffectPreferences.setVirtualizerProgress(progress)
    }
}
