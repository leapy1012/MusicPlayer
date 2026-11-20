package gd.app.musicplayer.core.designsystem.view

import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.domain.model.MusicSet
import gd.app.musicplayer.ui.selection.ScrollAwareAdapter
import kotlin.math.roundToInt

/**
 * Port of `com.ijoysoft.music.view.index.d`.
 *
 * When the list is sorted by a name-like column the bar runs in letters mode; otherwise it falls
 * back to the proportional fast-scroll thumb driven by [ScrollAwareAdapter] (original `o5.c`).
 */
class RecyclerIndexBarBinder(
    private val recyclerView: RecyclerView,
    private val indexBar: RecyclerIndexBar,
    /** Rows rendered above the data list (e.g. the artist album header). */
    private val headerCount: () -> Int = { 0 },
    /** Resolves the scroll calculator when the RecyclerView is driven by a ConcatAdapter. */
    private val scrollAwareAdapter: () -> ScrollAwareAdapter<*>? = {
        recyclerView.adapter as? ScrollAwareAdapter<*>
    }
) {

    private var letters: List<LetterPosition> = emptyList()
    private var suppressNextScrollUpdate = false
    private var fastScrollRetryScheduled = false

    /**
     * A bind can land before the list has been laid out, when the scroll calculator still measures
     * zero. Retry once on the next frame instead of leaving the bar permanently disabled.
     */
    private val fastScrollRetry = Runnable { applyFastScrollMode() }

    private val scrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrollStateChanged(view: RecyclerView, newState: Int) {
            indexBar.onScrollStateChanged(newState)
        }

        override fun onScrolled(view: RecyclerView, dx: Int, dy: Int) {
            if (suppressNextScrollUpdate) {
                suppressNextScrollUpdate = false
                return
            }
            syncIndicator()
        }
    }

    init {
        recyclerView.addOnScrollListener(scrollListener)

        indexBar.onLetterSelected = { letter ->
            suppressNextScrollUpdate = true
            layoutManager()?.scrollToPositionWithOffset(letter.position + headerCount(), 0)
        }

        indexBar.onFastScrollPercent = { percent ->
            val adapter = scrollAwareAdapter()
            if (adapter != null) {
                adapter.scrollToProgress(percent)
            } else {
                scrollToProgressByScrollMetrics(percent)
            }
        }
    }

    fun detach() {
        recyclerView.removeCallbacks(fastScrollRetry)
        recyclerView.removeOnScrollListener(scrollListener)
        indexBar.onLetterSelected = null
        indexBar.onFastScrollPercent = null
        indexBar.disable()
    }

    /** `index.d.j`: title/album/artist sorts index by letter, anything else by scroll percent. */
    fun bindTracks(
        musicSet: MusicSet,
        tracks: List<Music>,
        sortStyle: String,
        reversed: Boolean
    ) {
        fastScrollRetryScheduled = false

        if (tracks.isEmpty()) {
            indexBar.disable()
            return
        }

        val supportsLetters = when (musicSet) {
            is MusicSet.Tracks,
            is MusicSet.Album,
            is MusicSet.Artist,
            is MusicSet.Folder,
            is MusicSet.Genre,
            is MusicSet.Playlist,
            is MusicSet.Favorites -> true

            else -> false
        }

        val selector: ((Music) -> String?)? = when {
            !supportsLetters -> null
            sortStyle == "title" -> { music -> music.title }
            sortStyle == "album" -> { music -> music.album }
            sortStyle == "artist" -> { music -> music.artist }
            else -> null
        }

        if (selector == null) {
            applyFastScrollMode()
            return
        }

        applyLetters(LetterIndex.build(tracks.map(selector), reversed))
    }

    /** `index.d.h`: album/artist/genre/folder set lists index by name. */
    fun bindMusicSets(
        type: MusicSet,
        sets: List<MusicSet>,
        sortStyle: String,
        reversed: Boolean
    ) {
        fastScrollRetryScheduled = false

        if (sets.isEmpty()) {
            indexBar.disable()
            return
        }

        val indexableSortStyle = when (type) {
            is MusicSet.Albums -> "album"
            is MusicSet.Artists -> "artist"
            is MusicSet.Genres -> "genres"
            is MusicSet.Folders -> "name"
            else -> null
        }

        if (indexableSortStyle == null || indexableSortStyle != sortStyle) {
            applyFastScrollMode()
            return
        }

        val names = sets.map { set ->
            if (set is MusicSet.Folder) {
                set.folderPath.substringAfterLast('/').ifBlank { set.name }
            } else {
                set.name
            }
        }

        applyLetters(LetterIndex.build(names, reversed))
    }

    private fun applyLetters(newLetters: List<LetterPosition>) {
        if (newLetters.isEmpty()) {
            indexBar.disable()
            return
        }

        letters = newLetters
        indexBar.setLetters(newLetters)
        syncIndicator()
    }

    private fun applyFastScrollMode() {
        letters = emptyList()

        // ScrollAwareAdapter.getScrollProgress clears the RecyclerView padding (original o5.c), so
        // percent mode is only safe on the unpadded list layouts.
        if (recyclerView.paddingTop != 0 || recyclerView.paddingBottom != 0) {
            indexBar.disable()
            return
        }

        val adapter = scrollAwareAdapter()
        if (adapter == null) {
            applyScrollMetricsFastScrollMode()
            return
        }

        adapter.refreshScrollCalculator()

        // Original index.d.c.b: skip the thumb when the content barely exceeds one screen.
        val screenHeight = recyclerView.resources.displayMetrics.heightPixels
        if (adapter.getContentExtent() <= screenHeight) {
            indexBar.disable()

            if (!fastScrollRetryScheduled) {
                fastScrollRetryScheduled = true
                recyclerView.post(fastScrollRetry)
            }
            return
        }

        indexBar.setFastScrollEnabled()
        indexBar.setFastScrollPercent(adapter.getScrollProgress())
    }

    /**
     * Fallback for lists whose adapter cannot extend [ScrollAwareAdapter] because it already
     * extends `ListAdapter` (music-set lists). Their rows are uniform, so RecyclerView's own
     * scroll metrics give the same thumb position the original `o5.c` calculator would.
     */
    private fun applyScrollMetricsFastScrollMode() {
        val range = recyclerView.computeVerticalScrollRange()
        val extent = recyclerView.computeVerticalScrollExtent()

        // Original index.d.c.b: skip the thumb when the content barely exceeds one screen.
        val screenHeight = recyclerView.resources.displayMetrics.heightPixels
        if (range <= extent || range <= screenHeight) {
            indexBar.disable()

            if (!fastScrollRetryScheduled) {
                fastScrollRetryScheduled = true
                recyclerView.post(fastScrollRetry)
            }
            return
        }

        indexBar.setFastScrollEnabled()
        indexBar.setFastScrollPercent(scrollMetricsProgress())
    }

    private fun scrollMetricsProgress(): Float {
        val travel =
            recyclerView.computeVerticalScrollRange() - recyclerView.computeVerticalScrollExtent()
        if (travel <= 0) return 0f
        return recyclerView.computeVerticalScrollOffset().toFloat() / travel
    }

    private fun scrollToProgressByScrollMetrics(percent: Float) {
        val travel =
            recyclerView.computeVerticalScrollRange() - recyclerView.computeVerticalScrollExtent()
        if (travel <= 0) return
        val target = (percent * travel).roundToInt()
        recyclerView.scrollBy(0, target - recyclerView.computeVerticalScrollOffset())
    }

    private fun syncIndicator() {
        val layoutManager = layoutManager() ?: return

        if (letters.isNotEmpty()) {
            indexBar.setCurrentLetter(
                LetterIndex.letterAt(
                    letters = letters,
                    position = layoutManager.findFirstVisibleItemPosition(),
                    headerCount = headerCount()
                )
            )
            return
        }

        val adapter = scrollAwareAdapter()
        if (adapter != null) {
            indexBar.setFastScrollPercent(adapter.getScrollProgress())
        } else {
            indexBar.setFastScrollPercent(scrollMetricsProgress())
        }
    }

    private fun layoutManager(): LinearLayoutManager? =
        recyclerView.layoutManager as? LinearLayoutManager

    private fun scrollAwareAdapter(): ScrollAwareAdapter<*>? = scrollAwareAdapter.invoke()
}
