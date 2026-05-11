package gd.app.musicplayer.domain.usecase.theme

import gd.app.musicplayer.domain.model.ThemeGroup
import gd.app.musicplayer.domain.repository.ThemeRepo
import javax.inject.Inject

class ParseThemesUseCase @Inject constructor(
    private val themeRepo: ThemeRepo
) {
    operator fun invoke(): List<ThemeGroup> = themeRepo.getThemeGroups()
}
