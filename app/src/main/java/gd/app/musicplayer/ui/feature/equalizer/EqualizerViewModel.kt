package gd.app.musicplayer.ui.feature.equalizer

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
            soundEffectPreferences.setEqualizerEnabled(enabled)
        }
    }

    fun setBandMode(bandMode: Int) {
        viewModelScope.launch {
            soundEffectPreferences.setEqualizerBandMode(bandMode)
        }
    }

    fun setSelectedEffectId(effectId: Int) {
        viewModelScope.launch {
            val bandMode = settings.value.bandMode
            soundEffectPreferences.setLastEffectId(bandMode, effectId)
        }
    }

    fun setBassEnabled(enabled: Boolean) {
        viewModelScope.launch {
            soundEffectPreferences.setBassEnabled(enabled)
        }
    }

    fun setBassProgress(progress: Float) {
        viewModelScope.launch {
            soundEffectPreferences.setBassProgress(progress)
        }
    }

    fun setBassPresetId(presetId: Int) {
        viewModelScope.launch {
            soundEffectPreferences.setBassPresetId(presetId)
        }
    }

    fun setVirtualizerEnabled(enabled: Boolean) {
        viewModelScope.launch {
            soundEffectPreferences.setVirtualizerEnabled(enabled)
        }
    }

    fun setVirtualizerProgress(progress: Float) {
        viewModelScope.launch {
            soundEffectPreferences.setVirtualizerProgress(progress)
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
}