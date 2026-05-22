package gd.app.musicplayer.domain.repository

import gd.app.musicplayer.domain.model.Music

interface TrackDeletionGateway {
    fun deleteTrackFromStorage(track: Music): Boolean

    fun deleteDeletedTrackSource(track: Music): Boolean
}
