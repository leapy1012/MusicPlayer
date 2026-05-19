package gd.app.musicplayer.ui.theme

import android.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import gd.app.musicplayer.data.local.preference.ThemeSettings
import gd.app.musicplayer.domain.usecase.theme.GetThemeSettingsUseCase
import gd.app.musicplayer.domain.usecase.theme.UpdateThemeAppearanceUseCase
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val DEFAULT_THEME_EDIT_OVERLAY_COLOR = 855638016

data class ThemeEditUiState(
    val sourceImageName: String = "",
    val sourceOverlayColor: Int = DEFAULT_THEME_EDIT_OVERLAY_COLOR,
    val sourceBlur: Int = 0,

    val imageName: String = "",
    val overlayColor: Int = DEFAULT_THEME_EDIT_OVERLAY_COLOR,
    val blur: Int = 0,

    val isLoading: Boolean = true
) {
    val hasChanges: Boolean
        get() = imageName != sourceImageName ||
                overlayColor != sourceOverlayColor ||
                blur != sourceBlur
}

@HiltViewModel
class ThemeEditViewModel @Inject constructor(
    private val getThemeSettingsUseCase: GetThemeSettingsUseCase,
    private val updateThemeAppearanceUseCase: UpdateThemeAppearanceUseCase
) : ViewModel() {

    private var sourceSettings: ThemeSettings? = null

    private val _uiState = MutableStateFlow(ThemeEditUiState())
    val uiState: StateFlow<ThemeEditUiState> = _uiState.asStateFlow()

    init {
        loadThemeSettings()
    }

    fun onOverlayAlphaChanged(alpha: Int) {
        _uiState.value = _uiState.value.copy(
            overlayColor = Color.argb(
                alpha.coerceIn(0, 255),
                0,
                0,
                0
            )
        )
    }

    fun onBlurChanged(blur: Int) {
        _uiState.value = _uiState.value.copy(
            blur = blur.coerceIn(0, MAX_BLUR_RADIUS)
        )
    }

    fun onImageChanged(imageName: String) {
        val currentState = _uiState.value

        _uiState.value = if (currentState.imageName == imageName) {
            currentState
        } else {
            currentState.copy(
                imageName = imageName,
                overlayColor = DEFAULT_OVERLAY_COLOR,
                blur = 0
            )
        }
    }

    fun restoreDefaults() {
        val source = sourceSettings ?: return

        _uiState.value = _uiState.value.copy(
            imageName = source.imageName,
            overlayColor = DEFAULT_OVERLAY_COLOR,
            blur = 0
        )
    }

    fun save() {
        val state = _uiState.value

        viewModelScope.launch {
            updateThemeAppearanceUseCase(
                imageName = state.imageName,
                overlayColor = state.overlayColor,
                blur = state.blur
            )

            sourceSettings = ThemeSettings(
                imageName = state.imageName,
                overlayColor = state.overlayColor,
                blur = state.blur
            )

            _uiState.value = state.copy(
                sourceImageName = state.imageName,
                sourceOverlayColor = state.overlayColor,
                sourceBlur = state.blur
            )
        }
    }

    private fun loadThemeSettings() {
        viewModelScope.launch {
            val settings = getThemeSettingsUseCase()
            sourceSettings = settings

            _uiState.value = ThemeEditUiState(
                sourceImageName = settings.imageName,
                sourceOverlayColor = settings.overlayColor,
                sourceBlur = settings.blur,

                imageName = settings.imageName,
                overlayColor = settings.overlayColor,
                blur = settings.blur,

                isLoading = false
            )
        }
    }

    companion object {
        const val DEFAULT_OVERLAY_COLOR = DEFAULT_THEME_EDIT_OVERLAY_COLOR
        const val MAX_BLUR_RADIUS = 50
    }
}
