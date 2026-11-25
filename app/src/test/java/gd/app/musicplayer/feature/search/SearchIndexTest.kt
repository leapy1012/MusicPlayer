package gd.app.musicplayer.feature.search

import android.app.Application
import gd.app.musicplayer.R
import gd.app.musicplayer.domain.model.ListItem
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.domain.model.MusicSet
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, manifest = Config.NONE)
class SearchIndexTest {
    private val song = Music(1, "Track One", "Alice", "Album", "1", 1, duration = 60_000)
    private fun index(tracks: List<Music> = listOf(song)) = SearchIndex(
        tracks, emptyList(), listOf(MusicSet.Artist(2, "Alice", 1, 1, null)), emptyList(), emptyList()
    )

    @Test fun unchangedMatchesStillPublishTheLatestQueryAndReuseRowObjects() {
        val index = index()
        val initial = index.search(" t ")
        val state = MutableStateFlow(initial)
        state.value = index.search("TR")
        assertEquals("tr", state.value.query)
        assertEquals(initial.sections, state.value.sections)
        assertSame(initial.sections.single().items.single(), state.value.sections.single().items.single())
        assertNotEquals(initial, state.value)
    }

    @Test fun matchingUsesTitleArtistAndCategoryNameWithoutChangingOrder() {
        val index = index()
        val result = index.search(" ALI ")
        assertEquals("ali", result.query)
        assertEquals(listOf(R.string.songs, R.string.artists), result.sections.map { it.titleRes })
        assertSame(song, (result.sections.first().items.single() as ListItem.MusicItem).music)
        assertTrue(index.search("no matching name").sections.isEmpty())
        assertEquals(2, index.search("  ").sections.size)
    }

    @Test fun updatedLibraryDataBuildsANewIndexWithUpdatedMetadata() {
        val original = index()
        val changed = index(listOf(song.copy(title = "Renamed")))
        assertTrue(original.search("track").sections.isNotEmpty())
        assertTrue(changed.search("track").sections.isEmpty())
        assertEquals("Renamed", (changed.search("renamed").sections.single().items.single() as ListItem.MusicItem).music.title)
    }
}
