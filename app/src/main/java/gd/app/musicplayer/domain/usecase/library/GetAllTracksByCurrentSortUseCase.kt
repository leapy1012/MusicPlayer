package gd.app.musicplayer.domain.usecase.library

import android.content.Context
import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.data.model.MusicSet
import gd.app.musicplayer.data.repo.MainRepo
import gd.app.musicplayer.util.PreferenceUtil
import kotlinx.coroutines.flow.first

class GetAllTracksByCurrentSortUseCase(
    private val mainRepo: MainRepo
) {
    suspend operator fun invoke(context: Context): List<Music> {
        val preferenceUtil = PreferenceUtil.getInstance(context)
        return mainRepo.observeTracks(
            musicSet = MusicSet.Tracks,
            sortStyle = preferenceUtil.getSortStyle(MusicSet.Tracks),
            sortDescending = preferenceUtil.isSortReversed(MusicSet.Tracks, false)
        ).first()
    }
}

