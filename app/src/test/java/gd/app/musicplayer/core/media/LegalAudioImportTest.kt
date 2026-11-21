// Leapy_tts added begin: Real Room import tests with isolated temporary files and a controlled wrapper.
package gd.app.musicplayer.core.media

import android.app.Application
import androidx.room.Room
import gd.app.musicplayer.core.database.MusicDatabase
import gd.app.musicplayer.core.database.entity.MusicEntity
import gd.app.musicplayer.domain.repository.ScanRepo
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, manifest = Config.NONE)
class LegalAudioImportTest {
    private lateinit var db: MusicDatabase
    private lateinit var folder: File
    private val api = TestApi()
    private lateinit var policy: LegalAudioFilePolicy
    private lateinit var repo: ScanRepo

    internal class TestApi : LegalAudioFilePolicy.Api {
        override val nativeResult = 11
        override val selfResult = 12
        val results = mutableMapOf<String, Int>()
        val checked = mutableListOf<String>()
        val deleted = mutableListOf<String>()
        var failCheck = false
        var failDeletion = false
        override fun check(path: String): Int {
            checked += path
            if (failCheck) error("API unavailable")
            return results[path] ?: nativeResult
        }
        override fun delete(path: String) {
            deleted += path
            if (failDeletion) throw SecurityException("Scoped storage denied deletion")
            check(File(path).delete())
        }
    }

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), MusicDatabase::class.java)
            .allowMainThreadQueries().build()
        folder = java.nio.file.Files.createTempDirectory("legal-audio-test-").toFile()
        policy = LegalAudioFilePolicy(api)
        repo = ScanRepo(db.libraryDao(), policy)
    }
    @After fun close() { db.close(); folder.deleteRecursively() }
    private fun track(id: Long): MusicEntity {
        val file = File(folder, "track-$id.mp3").apply { writeText("test audio $id") }
        return MusicEntity(id, "track $id", file.absolutePath, duration = 1000)
    }

    @Test fun `only NAT and SELF enter Room and rejected files use wrapper deletion`() = runBlocking {
        val tracks = (1L..7L).map(::track)
        tracks.forEachIndexed { i, item -> api.results[item.data] = listOf(11, 12, 0, 3, 4, 5, 99)[i] }
        val accepted = repo.upsertTracks(tracks)
        assertEquals(listOf(1L, 2L), accepted.map { it.id })
        assertEquals(listOf(1L, 2L), db.libraryDao().getAllMusicForSync().map { it.id }.sorted())
        assertEquals(tracks.map { it.data }, api.checked)
        assertEquals(tracks.drop(2).map { it.data }, api.deleted)
        assertTrue(tracks.take(2).all { File(it.data).isFile })
        assertTrue(tracks.drop(2).none { File(it.data).exists() })
    }

    @Test fun `incremental scan rechecks unchanged files and hides rejected existing rows`() = runBlocking {
        val item = track(1)
        repo.upsertTracks(listOf(item))
        api.checked.clear()
        api.results[item.data] = 0
        assertTrue(repo.upsertChangedTracks(listOf(item)).isEmpty())
        assertEquals(listOf(item.data), api.checked)
        assertEquals(0, db.libraryDao().getMusicEntityById(item.id)!!.visible)
        assertFalse(File(item.data).exists())
    }

    @Test fun `API failure cannot delete a file or mark an existing row deleted`() = runBlocking {
        val item = track(1)
        repo.upsertTracks(listOf(item))
        api.failCheck = true
        val error = runCatching { repo.upsertChangedTracks(listOf(item)) }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
        assertTrue(api.deleted.isEmpty())
        assertTrue(File(item.data).exists())
        assertEquals(1, db.libraryDao().getMusicEntityById(item.id)!!.visible)
    }

    @Test fun `rejected file remains excluded even when deletion is denied`() = runBlocking {
        val item = track(1)
        api.results[item.data] = 4
        api.failDeletion = true
        assertTrue(repo.upsertTracks(listOf(item)).isEmpty())
        assertTrue(db.libraryDao().getAllMusicForSync().isEmpty())
        assertEquals(listOf(item.data), api.deleted)
        assertTrue(File(item.data).isFile)
    }
}
// Leapy_tts end
