package gd.app.musicplayer.domain.usecase.preferences

import gd.app.musicplayer.data.repo.UserPreferencesRepo
import gd.app.musicplayer.util.LibraryTabConfig
import javax.inject.Inject

class GetLibraryTabConfigsUseCase @Inject constructor(
    private val preferencesRepo: UserPreferencesRepo
) {
    operator fun invoke(): List<LibraryTabConfig> = preferencesRepo.getLibraryTabConfigs()
}
