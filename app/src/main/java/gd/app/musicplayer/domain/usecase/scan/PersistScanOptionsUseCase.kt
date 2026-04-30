package gd.app.musicplayer.domain.usecase.scan

import gd.app.musicplayer.data.repo.UserPreferencesRepo
import gd.app.musicplayer.ui.feature.scan.ScanOptions
import javax.inject.Inject

class PersistScanOptionsUseCase @Inject constructor(
    private val preferencesRepo: UserPreferencesRepo
) {
    operator fun invoke(options: ScanOptions) = preferencesRepo.persistScanOptions(options)
}
