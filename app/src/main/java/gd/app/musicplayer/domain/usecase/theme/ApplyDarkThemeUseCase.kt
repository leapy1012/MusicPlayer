package gd.app.musicplayer.domain.usecase.theme

import gd.app.musicplayer.domain.repository.ThemeRepo
import javax.inject.Inject

class ApplyDarkThemeUseCase @Inject constructor(
    private val themeRepo: ThemeRepo
) {
    operator fun invoke() {
        themeRepo.toggleDarkMode(true)
    }
}
