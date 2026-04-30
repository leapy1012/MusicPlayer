package gd.app.musicplayer.domain.usecase.scan

import gd.app.musicplayer.data.repo.UserPreferencesRepo
import gd.app.musicplayer.ui.feature.scan.ScanOptions
import javax.inject.Inject

class LoadScanOptionsUseCase @Inject constructor(
    private val preferencesRepo: UserPreferencesRepo
) {
    operator fun invoke(): ScanOptions = preferencesRepo.loadScanOptions()
}
