package gd.app.musicplayer.domain.usecase.theme

import android.content.Context
import gd.app.musicplayer.data.repo.ThemeRepo
import javax.inject.Inject

class UpdateThemeAppearanceUseCase @Inject constructor(
    private val themeRepo: ThemeRepo
) {
    operator fun invoke(context: Context, imageName: String, overlayColor: Int, blur: Int) =
        themeRepo.updateThemeAppearance(context, imageName, overlayColor, blur)
}
