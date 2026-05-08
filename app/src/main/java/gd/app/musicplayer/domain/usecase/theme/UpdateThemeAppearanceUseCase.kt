package gd.app.musicplayer.domain.usecase.theme

import android.content.Context
import gd.app.musicplayer.data.repository.ThemeRepo
import javax.inject.Inject

class UpdateThemeAppearanceUseCase @Inject constructor(
    private val themeRepo: ThemeRepo
) {
    suspend operator fun invoke(imageName: String, overlayColor: Int, blur: Int) =
        themeRepo.updateThemeAppearance(imageName, overlayColor, blur)
}
