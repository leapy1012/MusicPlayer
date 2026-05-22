package gd.app.musicplayer.domain.usecase.scan

import gd.app.musicplayer.domain.repository.MediaLibraryScanner
import javax.inject.Inject

class ScanAudioFilesUseCase @Inject constructor(
    private val mediaLibraryScanner: MediaLibraryScanner
) {

    suspend operator fun invoke(
        selectedPaths: List<String>,
        onFindingFile: suspend (String) -> Unit,
        onParseProgress: suspend (Int) -> Unit
    ): List<String> {
        return mediaLibraryScanner.scanAudioFiles(
            selectedPaths = selectedPaths,
            onFindingFile = onFindingFile,
            onParseProgress = onParseProgress
        )
    }
}
