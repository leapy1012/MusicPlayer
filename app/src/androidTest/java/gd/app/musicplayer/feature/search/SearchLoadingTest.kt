package gd.app.musicplayer.feature.search

import android.content.Intent
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import gd.app.musicplayer.R
import gd.app.musicplayer.ui.shell.MainActivity
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchLoadingTest {
    @Test fun coldSearchDoesNotPublishAnEmptyResultBeforeItsSourcesLoad() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ) as MainActivity
        val finished = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()
        var collection: Job? = null
        try {
            instrumentation.runOnMainSync {
                val model = ViewModelProvider(activity).get(
                    "search-loading-${System.nanoTime()}", SearchViewModel::class.java
                )
                val source = SearchViewModel::class.java.getDeclaredField("searchSource")
                    .apply { isAccessible = true }.get(model) as StateFlow<*>
                assertNull("Results start as loading, not a false empty result", model.results.value)
                assertNull("The source snapshot must also start as loading", source.value)
                collection = activity.lifecycleScope.launch {
                    try {
                        val results = model.results.filterNotNull().first()
                        val snapshot = checkNotNull(source.value)
                        val tracks = snapshot.javaClass.getDeclaredField("tracks")
                            .apply { isAccessible = true }.get(snapshot) as List<*>
                        if (tracks.isNotEmpty()) {
                            assertTrue(results.sections.any { it.titleRes == R.string.songs && it.items.isNotEmpty() })
                            // Every distinct query must arrive, even if its matches stay identical.
                            val firstTrack = tracks.first() as gd.app.musicplayer.domain.model.Music
                            val prefix = firstTrack.title.take(2).lowercase()
                            model.setQuery(prefix)
                            model.results.filterNotNull().first { it.query == prefix }
                            val longerPrefix = firstTrack.title.take(3).lowercase()
                            model.setQuery(longerPrefix)
                            val next = model.results.filterNotNull().first { it.query == longerPrefix }
                            assertTrue(next.sections.any { section -> section.items.any {
                                (it as? gd.app.musicplayer.domain.model.ListItem.MusicItem)?.music?.id == firstTrack.id
                            } })
                        }
                        model.setQuery("no-match-${System.nanoTime()}")
                        val noMatches = model.results.filterNotNull().first { it.sections.isEmpty() }
                        assertTrue(noMatches.sections.isEmpty())
                        assertNotNull(source.value)
                    } catch (error: Throwable) {
                        failure.set(error)
                    } finally {
                        finished.countDown()
                    }
                }
            }
            assertTrue("Loaded results should arrive", finished.await(15, TimeUnit.SECONDS))
            failure.get()?.let { throw AssertionError("Search loading regression", it) }
        } finally {
            instrumentation.runOnMainSync {
                collection?.cancel()
                activity.finish()
            }
        }
    }
}
