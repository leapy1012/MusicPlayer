package gd.app.musicplayer.domain.usecase.scan

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import dagger.hilt.android.qualifiers.ApplicationContext
import gd.app.musicplayer.core.common.dispatcher.AppDispatchers
import java.io.File
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine

class ScanAudioFilesUseCase @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val dispatchers: AppDispatchers
) {

    suspend operator fun invoke(
        selectedPaths: List<String>,
        onFindingFile: suspend (String) -> Unit,
        onParseProgress: suspend (Int) -> Unit
    ): List<String> {
        val audioFiles = findAudioFiles(selectedPaths, onFindingFile)
        scanWithMediaScanner(audioFiles, onParseProgress)
        return audioFiles
    }

    private suspend fun findAudioFiles(
        selectedPaths: List<String>,
        onFindingFile: suspend (String) -> Unit
    ): List<String> = withContext(dispatchers.io) {
        val roots = selectedPaths
            .ifEmpty { defaultRoots() }
            .map(::File)
            .filter { file -> file.exists() && file.canRead() }
            .distinctBy { file -> file.absolutePath.normalizedPath() }

        val result = mutableListOf<String>()
        roots.forEach { root ->
            ensureActive()
            if (root.isFile) {
                if (root.isAudioFile()) {
                    result += root.absolutePath
                    onFindingFile(root.absolutePath)
                }
                return@forEach
            }

            root.walkTopDown()
                .onEnter { dir -> dir.canRead() && !dir.isHidden }
                .forEach { file ->
                    ensureActive()
                    if (file.isFile && file.isAudioFile()) {
                        result += file.absolutePath
                        onFindingFile(file.absolutePath)
                    }
                }
        }
        result
    }

    private suspend fun scanWithMediaScanner(
        files: List<String>,
        onParseProgress: suspend (Int) -> Unit
    ) {
        if (files.isEmpty()) return
        withContext(dispatchers.io) {
            var scanned = 0
            files.forEach { path ->
                ensureActive()
                scanSingleFile(path)
                scanned++
                onParseProgress((scanned * 100 / files.size).coerceIn(0, 100))
            }
        }
    }

    private suspend fun scanSingleFile(path: String) {
        suspendCancellableCoroutine { continuation ->
            MediaScannerConnection.scanFile(
                appContext,
                arrayOf(path),
                arrayOf(AUDIO_MIME_TYPE)
            ) { _, _ ->
                if (continuation.isActive) {
                    continuation.resume(Unit)
                }
            }
        }
    }

    private fun defaultRoots(): List<String> {
        val roots = mutableListOf<String>()
        runCatching { Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC) }
            .getOrNull()
            ?.absolutePath
            ?.let(roots::add)
        runCatching { Environment.getExternalStorageDirectory() }
            .getOrNull()
            ?.absolutePath
            ?.let(roots::add)
        return roots.distinctBy(String::normalizedPath)
    }

    private companion object {
        const val AUDIO_MIME_TYPE = "audio/*"
    }
}

private val AUDIO_EXTENSIONS = setOf(
    "mp3",
    "m4a",
    "aac",
    "flac",
    "wav",
    "ogg",
    "oga",
    "opus",
    "wma",
    "amr",
    "mid",
    "midi"
)

private fun File.isAudioFile(): Boolean {
    return extension.lowercase() in AUDIO_EXTENSIONS
}

private fun String.normalizedPath(): String {
    return replace('\\', '/').trimEnd('/')
}
