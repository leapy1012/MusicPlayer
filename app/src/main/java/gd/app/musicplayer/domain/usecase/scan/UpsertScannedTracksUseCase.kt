package gd.app.musicplayer.domain.usecase.scan

import gd.app.musicplayer.data.local.db.entity.MusicEntity
import gd.app.musicplayer.domain.repository.ScanRepo
import javax.inject.Inject

class UpsertScannedTracksUseCase @Inject constructor(
    private val scanRepo: ScanRepo
) {
    suspend operator fun invoke(tracks: List<MusicEntity>) = scanRepo.upsertTracks(tracks)
}
