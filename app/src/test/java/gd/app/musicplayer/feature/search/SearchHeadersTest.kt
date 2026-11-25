package gd.app.musicplayer.feature.search

import android.app.Activity
import android.app.Application
import android.view.View
import android.widget.FrameLayout
import gd.app.musicplayer.R
import gd.app.musicplayer.core.designsystem.theme.LightThemePalette
import gd.app.musicplayer.domain.model.ListItem
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.domain.model.MusicSet
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, manifest = Config.NONE)
class SearchHeadersTest {
    @Test fun unchangedMatchesRefreshOnlyHighlightAndIdenticalResultsDoNothing() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.setTheme(R.style.AppTheme)
        val adapter = SearchResultAdapter(activity, LightThemePalette(), combined = true)
        var refreshes = 0
        var structuralUpdates = 0
        var highlightUpdates = 0
        adapter.registerAdapterDataObserver(object : androidx.recyclerview.widget.RecyclerView.AdapterDataObserver() {
            override fun onChanged() { refreshes++ }
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) { structuralUpdates++ }
            override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) { structuralUpdates++ }
            override fun onItemRangeChanged(positionStart: Int, itemCount: Int, payload: Any?) {
                assertEquals("payload_query", payload)
                highlightUpdates++
            }
        })
        val index = SearchIndex(
            listOf(Music(1, "Track One", "Artist", "Album", "1", 1, duration = 60_000)),
            emptyList(), emptyList(), emptyList(), emptyList()
        )
        val highlight = SearchResultAdapter::class.java.getDeclaredMethod("highlight", String::class.java)
            .apply { isAccessible = true }
        for (query in listOf("t", "tr", "not found", "")) {
            val result = index.search(query)
            adapter.submitSections(result.sections, result.query)
            adapter.submitSections(result.sections, result.query)
            if (query == "t" || query == "tr") {
                val text = highlight.invoke(adapter, "Track One") as android.text.Spanned
                val spans = text.getSpans(0, text.length, android.text.style.ForegroundColorSpan::class.java)
                assertEquals(1, spans.size)
                assertEquals(query.length, text.getSpanEnd(spans.single()))
            }
        }
        assertEquals(0, refreshes)
        assertEquals(1, highlightUpdates)
        assertEquals(3, structuralUpdates)
    }

    @Test fun categoryHeaderKeepsSeeAllWithoutDropdownOrCollapseAction() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.setTheme(R.style.AppTheme)
        var selectedCategory = 0
        val adapter = SearchResultAdapter(activity, LightThemePalette(), combined = true)
        adapter.setListener(object : SearchResultAdapter.Listener {
            override fun onSongClicked(song: Music, preferredIndex: Int?) = Unit
            override fun onSongMenuClicked(song: Music) = Unit
            override fun onMusicSetClicked(musicSet: MusicSet) = Unit
            override fun onMusicSetMenuClicked(musicSet: MusicSet) = Unit
            override fun onSeeAllClicked(sectionTitleRes: Int) { selectedCategory = sectionTitleRes }
        })
        val tracks = (1L..7L).map {
            ListItem.MusicItem(Music(it, "Track $it", "Artist", "Album", "1", 1, duration = 60_000))
        }
        adapter.submitSections(listOf(SearchResultAdapter.SearchSection(R.string.songs, tracks)), "")
        val holder = adapter.onCreateViewHolder(FrameLayout(activity), adapter.getItemViewType(0))
        adapter.onBindViewHolder(holder, 0)
        // Category title and See all are the only header children.
        assertEquals(2, (holder.itemView as android.view.ViewGroup).childCount)
        assertFalse(holder.itemView.hasOnClickListeners())
        assertEquals(6, adapter.itemCount) // Original All preview: one header + five songs.
        val seeAll = holder.itemView.findViewById<View>(R.id.see_all_layout)
        assertEquals(View.VISIBLE, seeAll.visibility)
        seeAll.performClick()
        assertEquals(R.string.songs, selectedCategory)
    }

    @Test fun queryAndPlaybackPayloadsKeepArtworkAndUpdateMatchSpansTogether() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.setTheme(R.style.AppTheme)
        val adapter = SearchResultAdapter(activity, LightThemePalette(), combined = true)
        val track = Music(1, "Track One", "Artist", "Album", "1", 1, duration = 60_000)
        val sections = listOf(SearchResultAdapter.SearchSection(R.string.songs, listOf(ListItem.MusicItem(track))))
        adapter.submitSections(sections, "t")
        val holder = adapter.onCreateViewHolder(FrameLayout(activity), adapter.getItemViewType(1))
            as gd.app.musicplayer.ui.common.viewholder.MusicViewHolder
        val artwork = android.graphics.drawable.ColorDrawable(android.graphics.Color.MAGENTA)
        holder.binding.musicItemAlbum.setImageDrawable(artwork)
        adapter.submitSections(sections, "tr")
        adapter.onBindViewHolder(holder, 1, mutableListOf("payload_query", "payload_playback_state"))
        assertSame(artwork, holder.binding.musicItemAlbum.drawable)
        val text = holder.binding.musicItemTitle.text as android.text.Spanned
        val span = text.getSpans(0, text.length, android.text.style.ForegroundColorSpan::class.java).single()
        assertEquals(2, text.getSpanEnd(span))
        assertEquals("Artist", holder.binding.musicItemArtist.text.toString())
        var textReplacements = 0
        holder.binding.musicItemTitle.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { textReplacements++ }
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        })
        adapter.submitSections(sections, "tra")
        adapter.onBindViewHolder(holder, 1, mutableListOf("payload_query"))
        assertSame("Highlight changes retain the text buffer", text, holder.binding.musicItemTitle.text)
        assertEquals(0, textReplacements)
        val updated = text.getSpans(0, text.length, android.text.style.ForegroundColorSpan::class.java).single()
        assertEquals(3, text.getSpanEnd(updated))
    }

    @Test fun categoryChangesKeepSongRowsAndOnlyRefreshTheirHighlight() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.setTheme(R.style.AppTheme)
        val adapter = SearchResultAdapter(activity, LightThemePalette(), combined = true)
        val songs = SearchResultAdapter.SearchSection(R.string.songs, listOf(
            ListItem.MusicItem(Music(1, "Track One", "Artist", "Album", "1", 1, duration = 60_000))
        ))
        val artists = SearchResultAdapter.SearchSection(R.string.artists, listOf(
            ListItem.MusicSetItem(MusicSet.Artist(1, "Artist", 1, 1, null))
        ))
        adapter.submitSections(listOf(songs, artists), "t")
        var removed = 0
        var highlighted = 0
        adapter.registerAdapterDataObserver(object : androidx.recyclerview.widget.RecyclerView.AdapterDataObserver() {
            override fun onChanged() { fail("Unchanged songs must not be fully rebound") }
            override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
                assertEquals(2, positionStart)
                removed += itemCount
            }
            override fun onItemRangeChanged(positionStart: Int, itemCount: Int, payload: Any?) {
                assertEquals(1, positionStart)
                assertEquals("payload_query", payload)
                highlighted += itemCount
            }
        })
        adapter.submitSections(listOf(songs), "tr")
        assertEquals(2, removed)
        assertEquals(1, highlighted)
        assertEquals(2, adapter.itemCount)
    }
}
