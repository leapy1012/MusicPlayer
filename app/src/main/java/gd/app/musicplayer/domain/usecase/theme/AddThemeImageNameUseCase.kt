package gd.app.musicplayer.domain.usecase.theme

import gd.app.musicplayer.core.datastore.ThemeSettingPreferenceStore
import javax.inject.Inject

class AddThemeImageNameUseCase @Inject constructor(
    private val repo: ThemeSettingPreferenceStore
) {
    suspend operator fun invoke(fileName: String) = repo.addThemeImageUri(fileName)
}
