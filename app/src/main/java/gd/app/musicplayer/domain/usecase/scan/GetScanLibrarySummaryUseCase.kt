package gd.app.musicplayer.domain.usecase.scan

import gd.app.musicplayer.domain.repository.ScanRepo
import gd.app.musicplayer.ui.scan.ScanLibraryInfo
import javax.inject.Inject

class GetScanLibrarySummaryUseCase @Inject constructor(
    private val scanRepo: ScanRepo
) {
    suspend operator fun invoke(): ScanLibraryInfo {
        val summary = scanRepo.librarySummary()
        return ScanLibraryInfo(
            songs = summary.songs,
            albums = summary.albums,
            artists = summary.artists
        )
    }
}
