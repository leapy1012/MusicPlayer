package gd.app.musicplayer.feature.search

import android.content.Intent
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import gd.app.musicplayer.R
import gd.app.musicplayer.ui.common.base.RecyclerEmptyStateController
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchVisibilityTest {
    @Test fun hiddenPagesStayIdleAndSelectedPagesCatchUpToLatestQuery() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, SearchActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ) as SearchActivity
        val finished = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()
        try {
            instrumentation.runOnMainSync {
                activity.lifecycleScope.launch {
                    try {
                        activity.supportFragmentManager.executePendingTransactions()
                        val parent = activity.supportFragmentManager.fragments.filterIsInstance<SearchFragment>().single()
                        val model = ViewModelProvider(parent)[SearchViewModel::class.java]
                        model.results.filterNotNull().first()
                        delay(500)
                        fun page(index: Int) = parent.childFragmentManager.findFragmentByTag("f$index") as SearchPageFragment
                        fun list(index: Int) = page(index).requireView().findViewById<RecyclerView>(R.id.recyclerview)
                        fun emptyShown(index: Int): Boolean {
                            val controller = SearchPageFragment::class.java.getDeclaredField("emptyStateController")
                                .apply { isAccessible = true }.get(page(index))
                            return RecyclerEmptyStateController::class.java.getDeclaredField("isEmptyShown")
                                .apply { isAccessible = true }.getBoolean(controller)
                        }
                        val query = "no-match-${System.nanoTime()}"
                        model.setQuery(query)
                        model.results.filterNotNull().first { it.query == query }
                        delay(250)
                        assertTrue(emptyShown(0))
                        assertFalse("Hidden Songs must not start its rabbit", emptyShown(1))
                        parent.selectPage(SearchPage.SONGS)
                        delay(600)
                        assertTrue("Selected Songs catches up to the empty query", emptyShown(1))
                        model.setQuery("")
                        val results = model.results.filterNotNull().first { it.query.isEmpty() }
                        delay(250)
                        assertEquals("Hidden All keeps its previous snapshot", 0, list(0).adapter!!.itemCount)
                        parent.selectPage(SearchPage.ALL)
                        delay(600)
                        if (results.sections.isNotEmpty()) {
                            assertTrue("Returning All renders the latest matches", list(0).adapter!!.itemCount > 0)
                            assertFalse(emptyShown(0))
                        }
                    } catch (error: Throwable) {
                        failure.set(error)
                    } finally {
                        finished.countDown()
                    }
                }
            }
            assertTrue("Search page verification timed out", finished.await(15, TimeUnit.SECONDS))
            failure.get()?.let { throw it }
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }
}
