package gd.app.musicplayer.ui.feature.selection

import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.data.model.MusicSet
import gd.app.musicplayer.data.repo.LibraryRepo
import gd.app.musicplayer.data.repo.PlaylistRepo
import gd.app.musicplayer.util.PreferenceUtil
import kotlinx.coroutines.flow.first
import java.util.Locale
import javax.inject.Inject

class LoadMusicSelectDataUseCase @Inject constructor(
    private val libraryRepo: LibraryRepo,
    private val playlistRepo: PlaylistRepo,
    private val preferenceUtil: PreferenceUtil
) {
    suspend operator fun invoke(request: MusicSelectLoadRequest): MusicSelectLoadResult {
        if (request.sourceMode) {
            val sourceSets = sortSourceItems(
                libraryRepo.observeMusicSets(MusicSet.Folders).first().filterIsInstance<MusicSet.Folder>(),
                MusicSet.Folders
            )
            return MusicSelectLoadResult(
                sourceSets = sourceSets
            )
        }

        val normalizedSet = normalizeSongsSetForSort(request.selectedSet)
        val selectedSetSongs = libraryRepo.observeTracks(
            musicSet = normalizedSet,
            sortStyle = preferenceUtil.getSortStyle(
                normalizedSet,
                selectionMode = true
            ),
            sortDescending = preferenceUtil.isSortReversed(
                normalizedSet,
                selectionMode = true
            )
        ).first()

        val targetSetSongs = request.targetSet
            ?.takeIf { it is MusicSet.TrackCollection }
            ?.let {
                libraryRepo.observeTracks(
                    musicSet = it,
                    sortStyle = "title",
                    sortDescending = false
                ).first()
            }
            ?: emptyList()

        val spinnerCandidates = if (request.selectedSet is MusicSet.Folder) {
            sortSourceItems(
                libraryRepo.observeMusicSets(MusicSet.Folders).first().filterIsInstance<MusicSet.Folder>(),
                MusicSet.Folders
            )
        } else {
            playlistRepo.observeSelectablePlaylists().first()
                .filterNot { it.id == request.targetSet?.id }
        }

        return MusicSelectLoadResult(
            selectedSetSongs = selectedSetSongs,
            targetSetSongs = targetSetSongs,
            spinnerCandidates = spinnerCandidates
        )
    }

    private fun normalizeSongsSetForSort(set: MusicSet): MusicSet {
        return if (set is MusicSet.TrackCollection) set else MusicSet.Tracks
    }

    private fun sortSourceItems(items: List<MusicSet>, sourceCategory: MusicSet): List<MusicSet> {
        return when (sourceCategory) {
            is MusicSet.Folders -> {
                val folders = items.filterIsInstance<MusicSet.Folder>()
                val style = preferenceUtil.getFolderSortStyle(selectionMode = true)
                val reversed = preferenceUtil.isFolderSortReversed(selectionMode = true)
                val comparator = when (style) {
                    "track_count" -> compareBy<MusicSet.Folder>(
                        { it.musicCount },
                        { it.name.lowercase(Locale.getDefault()) },
                        { it.id }
                    )

                    "date" -> compareBy<MusicSet.Folder>(
                        { it.date },
                        { it.name.lowercase(Locale.getDefault()) },
                        { it.id }
                    )

                    else -> compareBy<MusicSet.Folder>(
                        { it.name.lowercase(Locale.getDefault()) },
                        { it.id }
                    )
                }
                val sorted = folders.sortedWith(comparator)
                when {
                    style == "title_desc" -> sorted.asReversed()
                    reversed -> sorted.asReversed()
                    else -> sorted
                }
            }

            is MusicSet.Artists -> {
                val artists = items.filterIsInstance<MusicSet.Artist>()
                val style = preferenceUtil.getArtistSortStyle()
                val reversed = preferenceUtil.isArtistSortReversed()
                val comparator = when (style) {
                    "track_count" -> compareBy<MusicSet.Artist>(
                        { it.musicCount },
                        { it.name.lowercase(Locale.getDefault()) },
                        { it.id }
                    )

                    "album_count" -> compareBy<MusicSet.Artist>(
                        { it.albumCount },
                        { it.name.lowercase(Locale.getDefault()) },
                        { it.id }
                    )

                    else -> compareBy<MusicSet.Artist>(
                        { it.name.lowercase(Locale.getDefault()) },
                        { it.id }
                    )
                }
                val sorted = artists.sortedWith(comparator)
                when {
                    style == "title_desc" -> sorted.asReversed()
                    reversed -> sorted.asReversed()
                    else -> sorted
                }
            }

            is MusicSet.Albums -> {
                val albums = items.filterIsInstance<MusicSet.Album>()
                val style = preferenceUtil.getAlbumSortStyle()
                val reversed = preferenceUtil.isAlbumSortReversed()
                val comparator = when (style) {
                    "year" -> compareBy<MusicSet.Album>(
                        { it.year },
                        { it.name.lowercase(Locale.getDefault()) },
                        { it.id }
                    )

                    "artist" -> compareBy<MusicSet.Album>(
                        { it.artist.lowercase(Locale.getDefault()) },
                        { it.name.lowercase(Locale.getDefault()) },
                        { it.id }
                    )

                    "track_count" -> compareBy<MusicSet.Album>(
                        { it.musicCount },
                        { it.name.lowercase(Locale.getDefault()) },
                        { it.id }
                    )

                    "date" -> compareBy<MusicSet.Album>(
                        { it.date },
                        { it.name.lowercase(Locale.getDefault()) },
                        { it.id }
                    )

                    else -> compareBy<MusicSet.Album>(
                        { it.name.lowercase(Locale.getDefault()) },
                        { it.id }
                    )
                }
                val sorted = albums.sortedWith(comparator)
                when {
                    style == "title_desc" -> sorted.asReversed()
                    reversed -> sorted.asReversed()
                    else -> sorted
                }
            }

            is MusicSet.Genres -> {
                val genres = items.filterIsInstance<MusicSet.Genre>()
                val style = preferenceUtil.getSortStyle(sourceCategory, false)
                val reversed = preferenceUtil.isSortReversed(sourceCategory, false)
                val comparator = when (style) {
                    "track_count" -> compareBy<MusicSet.Genre>(
                        { it.musicCount },
                        { it.name.lowercase(Locale.getDefault()) },
                        { it.id }
                    )

                    else -> compareBy<MusicSet.Genre>(
                        { it.name.lowercase(Locale.getDefault()) },
                        { it.id }
                    )
                }
                val sorted = genres.sortedWith(comparator)
                when {
                    style == "title_desc" -> sorted.asReversed()
                    reversed -> sorted.asReversed()
                    else -> sorted
                }
            }

            else -> items.sortedBy { it.name.lowercase(Locale.getDefault()) }
        }
    }
}

class ConfirmMusicSelectUseCase @Inject constructor(
    private val playlistRepo: PlaylistRepo
) {
    suspend operator fun invoke(request: MusicSelectConfirmRequest): MusicSelectConfirmResult {
        val targetPlaylistId = request.targetSet.id
        val songIds = request.selectedSongs.map(Music::id).distinct()
        if (songIds.isEmpty() || targetPlaylistId <= 0L) {
            return MusicSelectConfirmResult(insertedCount = 0, skippedCount = songIds.size)
        }

        val insertedCount = playlistRepo.addTracksToPlaylists(
            playlistIds = listOf(targetPlaylistId),
            tracks = request.selectedSongs
        )

        return MusicSelectConfirmResult(
            insertedCount = insertedCount,
            skippedCount = songIds.size - insertedCount
        )
    }
}
