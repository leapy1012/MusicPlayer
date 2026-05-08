package gd.app.musicplayer.domain.usecase.theme

import android.content.Context
import gd.app.musicplayer.data.model.ThemeGroup
import gd.app.musicplayer.data.repository.ThemeRepo
import javax.inject.Inject

class ParseThemesUseCase @Inject constructor(
    private val themeRepo: ThemeRepo
) {
    operator fun invoke(): List<ThemeGroup> = themeRepo.getThemeGroups()
}
