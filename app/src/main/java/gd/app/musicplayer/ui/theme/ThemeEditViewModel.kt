package gd.app.musicplayer.ui.theme

import android.graphics.Color
import android.content.Context
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import gd.app.musicplayer.data.repo.ThemeRepo
import gd.app.musicplayer.data.repo.ThemeSettings
import gd.app.musicplayer.data.repo.UserPreferencesRepo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

private const val DEFAULT_THEME_EDIT_OVERLAY_COLOR = 855638016

data class ThemeEditUiState(
    val sourceImageName: String = "",
    val imageName: String = "",
    val overlayColor: Int = DEFAULT_THEME_EDIT_OVERLAY_COLOR,
    val blur: Int = 0
) {
    val hasChanges: Boolean
        get() = imageName != sourceImageName ||
            overlayColor != DEFAULT_THEME_EDIT_OVERLAY_COLOR ||
            blur != 0
}

@HiltViewModel
class ThemeEditViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val preferencesRepo: UserPreferencesRepo,
    private val themeRepo: ThemeRepo
) : ViewModel() {

    private val sourceSettings: ThemeSettings = preferencesRepo.getThemeSettings()
    private val _uiState = MutableStateFlow(
        ThemeEditUiState(
            sourceImageName = sourceSettings.imageName,
            imageName = sourceSettings.imageName,
            overlayColor = sourceSettings.overlayColor,
            blur = sourceSettings.blur
        )
    )
    val uiState: StateFlow<ThemeEditUiState> = _uiState.asStateFlow()

    fun onOverlayAlphaChanged(alpha: Int) {
        _uiState.value = _uiState.value.copy(
            overlayColor = Color.argb(alpha.coerceIn(0, 255), 0, 0, 0)
        )
    }

    fun onBlurChanged(blur: Int) {
        _uiState.value = _uiState.value.copy(blur = blur.coerceIn(0, MAX_BLUR_RADIUS))
    }

    fun onImageChanged(imageName: String) {
        val currentState = _uiState.value
        _uiState.value = if (currentState.imageName == imageName) {
            currentState.copy(imageName = imageName)
        } else {
            currentState.copy(
                imageName = imageName,
                overlayColor = DEFAULT_OVERLAY_COLOR,
                blur = 0
            )
        }
    }

    fun restoreDefaults() {
        _uiState.value = _uiState.value.copy(
            imageName = sourceSettings.imageName,
            overlayColor = DEFAULT_OVERLAY_COLOR,
            blur = 0
        )
    }

    fun save() {
        val state = _uiState.value
        themeRepo.updateThemeAppearance(
            context = appContext,
            imageName = state.imageName,
            overlayColor = state.overlayColor,
            blur = state.blur
        )
    }

    companion object {
        const val DEFAULT_OVERLAY_COLOR = DEFAULT_THEME_EDIT_OVERLAY_COLOR
        const val MAX_BLUR_RADIUS = 50
    }
}
