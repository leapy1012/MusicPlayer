package gd.app.musicplayer.ui.theme

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import gd.app.musicplayer.data.model.ThemeGroup
import gd.app.musicplayer.data.repo.ThemeSettings
import gd.app.musicplayer.domain.usecase.theme.AddThemeImageNameUseCase
import gd.app.musicplayer.domain.usecase.theme.ApplyPictureThemeUseCase
import gd.app.musicplayer.domain.usecase.theme.GetThemeSettingsUseCase
import gd.app.musicplayer.domain.usecase.theme.ObserveThemeSettingsUseCase
import gd.app.musicplayer.domain.usecase.theme.ParseThemesUseCase
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val DEFAULT_THEME_OVERLAY_COLOR = 855638016

data class ThemeUiState(
    val themes: List<ThemeGroup> = emptyList(),
    val settings: ThemeSettings? = null,
    val selectedTabIndex: Int = 0
)

sealed interface ThemeEffect {
    data class OpenAccentColorDialog(val currentColor: Int) : ThemeEffect
    data object OpenImagePicker : ThemeEffect
    data class OpenCropper(
        val sourceUri: Uri,
        val outputPath: String
    ) : ThemeEffect
    data object ApplyTheme : ThemeEffect
}

@HiltViewModel
class ThemeViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val parseThemesUseCase: ParseThemesUseCase,
    private val observeThemeSettingsUseCase: ObserveThemeSettingsUseCase,
    private val getThemeSettingsUseCase: GetThemeSettingsUseCase,
    private val addThemeImageNameUseCase: AddThemeImageNameUseCase,
    private val applyPictureThemeUseCase: ApplyPictureThemeUseCase
) : ViewModel() {

    private val parsedThemes: List<ThemeGroup> by lazy(LazyThreadSafetyMode.NONE) {
        parseThemesUseCase(appContext)
    }

    private val selectedTabIndex = MutableStateFlow(0)

    private val _effects = MutableSharedFlow<ThemeEffect>()
    val effects: SharedFlow<ThemeEffect> = _effects.asSharedFlow()

    private val initialSettings = getThemeSettingsUseCase()

    val uiState: StateFlow<ThemeUiState> = combine(
        observeThemeSettingsUseCase(),
        selectedTabIndex
    ) { settings, tabIndex ->
        ThemeUiState(
            themes = parsedThemes,
            settings = settings,
            selectedTabIndex = tabIndex
        )
    }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ThemeUiState(
                themes = parsedThemes,
                settings = initialSettings,
                selectedTabIndex = 0
            )
        )

    fun onAccentColorClicked(fallbackColor: Int) {
        val currentColor = uiState.value.settings?.accentColor ?: fallbackColor
        emitEffect(ThemeEffect.OpenAccentColorDialog(currentColor))
    }

    fun onPickCustomThemeClicked() {
        emitEffect(ThemeEffect.OpenImagePicker)
    }

    fun onCustomThemeImagePicked(uri: Uri) {
        val outputPath = ThemeBackgroundStore.createDraftBackgroundFile(appContext).absolutePath
        emitEffect(
            ThemeEffect.OpenCropper(
                sourceUri = uri,
                outputPath = outputPath
            )
        )
    }

    fun onThemeImageCropped(imagePath: String) {
        val persistedPath = persistCustomThemeImage(imagePath)
        addThemeImageNameUseCase(persistedPath)
        applyPictureThemeUseCase(
            context = appContext,
            imageName = persistedPath,
            overlayColor = DEFAULT_THEME_OVERLAY_COLOR,
            blur = 0
        )
        if (selectedTabIndex.value != 0) {
            selectedTabIndex.value = 0
        }
        emitEffect(ThemeEffect.ApplyTheme)
    }

    fun onThemeSelected(fileName: String, tabIndex: Int) {
        applyPictureThemeUseCase(
            context = appContext,
            imageName = fileName,
            overlayColor = DEFAULT_THEME_OVERLAY_COLOR,
            blur = 0
        )
        if (selectedTabIndex.value != tabIndex) {
            selectedTabIndex.value = tabIndex
        }
        emitEffect(ThemeEffect.ApplyTheme)
    }

    fun onTabSelected(tabIndex: Int) {
        if (selectedTabIndex.value != tabIndex) {
            selectedTabIndex.value = tabIndex
        }
    }

    private fun emitEffect(effect: ThemeEffect) {
        viewModelScope.launch {
            _effects.emit(effect)
        }
    }

    private fun persistCustomThemeImage(path: String): String {
        val draftFile = File(path)
        if (!draftFile.exists()) return path

        if (!ThemeBackgroundStore.isDraftThemePath(appContext, path)) {
            return draftFile.absolutePath
        }

        val savedFile = ThemeBackgroundStore.createSavedBackgroundFile(appContext)
        savedFile.parentFile?.mkdirs()

        runCatching {
            if (!draftFile.renameTo(savedFile)) {
                draftFile.copyTo(savedFile, overwrite = true)
                draftFile.delete()
            }
        }

        return savedFile.absolutePath
    }
}
