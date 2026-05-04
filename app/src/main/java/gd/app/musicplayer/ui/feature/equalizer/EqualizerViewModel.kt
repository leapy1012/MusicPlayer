package gd.app.musicplayer.ui.feature.equalizer

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import gd.app.musicplayer.app.AppDispatchers
import gd.app.musicplayer.data.repo.EqualizerPresetRecord
import gd.app.musicplayer.domain.usecase.equalizer.CreateEqualizerPresetUseCase
import gd.app.musicplayer.domain.usecase.equalizer.DeleteEqualizerPresetUseCase
import gd.app.musicplayer.domain.usecase.equalizer.GetEqualizerPresetByIdUseCase
import gd.app.musicplayer.domain.usecase.equalizer.LoadEqualizerPresetsUseCase
import gd.app.musicplayer.domain.usecase.equalizer.UpdateEqualizerPresetUseCase
import gd.app.musicplayer.domain.usecase.playback.ApplyAudioEffectsUseCase
import gd.app.musicplayer.playback.AudioEffectsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val CUSTOM_PRESET_ID = 1L
private const val DEFAULT_PRESET_FLAG = 1

data class EqualizerUiPreset(
    val id: Long,
    val name: String,
    val bands: IntArray,
    val presetFlag: Int
) {
    val isDefaultPreset: Boolean get() = presetFlag == DEFAULT_PRESET_FLAG
}

data class EqualizerScreenState(
    val settings: AudioEffectsManager.Settings? = null,
    val presets: List<EqualizerUiPreset> = emptyList()
)

@HiltViewModel
class EqualizerViewModel @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val loadEqualizerPresetsUseCase: LoadEqualizerPresetsUseCase,
    private val getEqualizerPresetByIdUseCase: GetEqualizerPresetByIdUseCase,
    private val createEqualizerPresetUseCase: CreateEqualizerPresetUseCase,
    private val updateEqualizerPresetUseCase: UpdateEqualizerPresetUseCase,
    private val deleteEqualizerPresetUseCase: DeleteEqualizerPresetUseCase,
    private val applyAudioEffectsUseCase: ApplyAudioEffectsUseCase,
    private val dispatchers: AppDispatchers
) : ViewModel() {

    private val _state = MutableStateFlow(EqualizerScreenState())
    val state: StateFlow<EqualizerScreenState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch(dispatchers.io) {
            var settings = AudioEffectsManager.loadSettings(appContext)
            val presets = load(settings.useTenBand)
            val selected = presets.firstOrNull { it.id == settings.selectedPresetIndex().toLong() }
            if (selected != null) {
                settings = if (settings.useTenBand) {
                    settings.copy(customTenBandLevels = selected.bands.copyOf())
                } else {
                    settings.copy(customFiveBandLevels = selected.bands.copyOf())
                }
                AudioEffectsManager.saveSettings(appContext, settings)
            }
            _state.value = EqualizerScreenState(settings = settings, presets = presets)
        }
    }

    fun updateSettings(transform: (AudioEffectsManager.Settings) -> AudioEffectsManager.Settings) {
        val current = _state.value.settings ?: return
        val updated = transform(current)
        _state.value = _state.value.copy(settings = updated)
        persistAndApply(updated)
    }

    fun onBandChanged(index: Int, levelMb: Int) {
        val settings = _state.value.settings ?: return
        val updated = if (settings.useTenBand) {
            val bands = settings.customTenBandLevels.copyOf().apply { this[index] = levelMb }
            settings.copy(customTenBandLevels = bands, selectedPresetIndexTenBand = CUSTOM_PRESET_ID.toInt())
        } else {
            val bands = settings.customFiveBandLevels.copyOf().apply { this[index] = levelMb }
            settings.copy(customFiveBandLevels = bands, selectedPresetIndexFiveBand = CUSTOM_PRESET_ID.toInt())
        }
        _state.value = _state.value.copy(settings = updated)
        persistAndApply(updated)
        viewModelScope.launch(dispatchers.io) {
            upsertCustomPreset(updated)
            _state.value = _state.value.copy(presets = load(updated.useTenBand))
        }
    }

    fun applyPreset(presetId: Long) {
        val settings = _state.value.settings ?: return
        val preset = _state.value.presets.firstOrNull { it.id == presetId } ?: return
        val updated = if (settings.useTenBand) {
            settings.copy(customTenBandLevels = preset.bands.copyOf(), selectedPresetIndexTenBand = presetId.toInt())
        } else {
            settings.copy(customFiveBandLevels = preset.bands.copyOf(), selectedPresetIndexFiveBand = presetId.toInt())
        }
        _state.value = _state.value.copy(settings = updated)
        persistAndApply(updated)
    }

    fun createPreset(name: String, bands: IntArray, onDone: () -> Unit) {
        val settings = _state.value.settings ?: return
        viewModelScope.launch(dispatchers.io) {
            val id = createEqualizerPresetUseCase(name, bands, settings.useTenBand, preset = 0)
            val updated = if (settings.useTenBand) settings.copy(selectedPresetIndexTenBand = id.toInt()) else settings.copy(selectedPresetIndexFiveBand = id.toInt())
            AudioEffectsManager.saveSettings(appContext, updated)
            applyAudioEffectsUseCase(appContext)
            _state.value = EqualizerScreenState(settings = updated, presets = load(updated.useTenBand))
            withContext(dispatchers.main) { onDone() }
        }
    }

    fun renamePreset(preset: EqualizerUiPreset, name: String, onDone: () -> Unit) {
        val settings = _state.value.settings ?: return
        viewModelScope.launch(dispatchers.io) {
            updateEqualizerPresetUseCase(preset.id, name, preset.bands, settings.useTenBand)
            _state.value = _state.value.copy(presets = load(settings.useTenBand))
            withContext(dispatchers.main) { onDone() }
        }
    }

    fun deletePreset(presetId: Long, onDone: () -> Unit) {
        val settings = _state.value.settings ?: return
        viewModelScope.launch(dispatchers.io) {
            deleteEqualizerPresetUseCase(presetId, settings.useTenBand)
            var updatedSettings = settings
            if (updatedSettings.selectedPresetIndex().toLong() == presetId) {
                val presets = load(settings.useTenBand)
                val fallback = presets.firstOrNull { it.id == CUSTOM_PRESET_ID } ?: presets.firstOrNull()
                if (fallback != null) {
                    updatedSettings = if (updatedSettings.useTenBand) {
                        updatedSettings.copy(customTenBandLevels = fallback.bands.copyOf(), selectedPresetIndexTenBand = fallback.id.toInt())
                    } else {
                        updatedSettings.copy(customFiveBandLevels = fallback.bands.copyOf(), selectedPresetIndexFiveBand = fallback.id.toInt())
                    }
                    AudioEffectsManager.saveSettings(appContext, updatedSettings)
                    applyAudioEffectsUseCase(appContext)
                }
            }
            _state.value = EqualizerScreenState(settings = updatedSettings, presets = load(updatedSettings.useTenBand))
            withContext(dispatchers.main) { onDone() }
        }
    }

    private suspend fun upsertCustomPreset(settings: AudioEffectsManager.Settings) {
        val existing = getEqualizerPresetByIdUseCase(CUSTOM_PRESET_ID, settings.useTenBand)
        if (existing == null) {
            createEqualizerPresetUseCase(
                name = "Custom",
                bands = settings.customLevels(),
                tenBand = settings.useTenBand,
                preset = DEFAULT_PRESET_FLAG
            )
        } else {
            updateEqualizerPresetUseCase(existing.id, existing.name, settings.customLevels(), settings.useTenBand)
        }
    }

    private suspend fun load(tenBand: Boolean): List<EqualizerUiPreset> {
        return loadEqualizerPresetsUseCase(tenBand).map { it.toUi() }
    }

    private fun EqualizerPresetRecord.toUi(): EqualizerUiPreset {
        return EqualizerUiPreset(id = id, name = name, bands = bands, presetFlag = preset)
    }

    private fun persistAndApply(settings: AudioEffectsManager.Settings) {
        AudioEffectsManager.saveSettings(appContext, settings)
        applyAudioEffectsUseCase(appContext)
    }
}
