package gd.app.musicplayer.playback.restore

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import gd.app.musicplayer.core.common.dispatcher.AppDispatchers
import gd.app.musicplayer.core.database.dao.MusicDao
import gd.app.musicplayer.core.datastore.PlaybackStatePreferenceStore
import gd.app.musicplayer.domain.repository.PlaybackQueueRepo
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Shared storage-side policy. Never mutates the live queue, player, notification or audio focus. */
@Singleton
class PlaybackResumeLoader @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val queueRepo: PlaybackQueueRepo,
    private val progressStore: PlaybackStatePreferenceStore,
    private val musicDao: MusicDao,
    private val dispatchers: AppDispatchers,
) {
    suspend fun load(includeLibraryFallback: Boolean = false): RestoredPlayback? = withContext(dispatchers.io) {
        val saved = queueRepo.getQueue().filter { PlaybackSourceValidator.isReadable(context, it) }
        val restored = PlaybackRestoreResolver.resolve(saved, progressStore.getMusicProgress())
        if (restored != null || !includeLibraryFallback) return@withContext restored
        musicDao.getVisibleTracksSnapshotForPlayback()
            .filter { PlaybackSourceValidator.isReadable(context, it) }
            .takeIf { it.isNotEmpty() }?.let { RestoredPlayback(it, 0, 0L) }
    }
}
