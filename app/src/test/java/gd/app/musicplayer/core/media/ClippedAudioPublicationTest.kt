// Leapy_tts added begin: Verify the real clip writer's pending-file signing and rollback sequence.
package gd.app.musicplayer.core.media

import android.app.Application
import android.common.DreamCreatedMediaManager as Hooks
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import gd.app.musicplayer.ui.editor.data.MediaStoreAudioTrimRepository
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import java.io.File
import java.lang.reflect.InvocationTargetException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, manifest = Config.NONE)
class ClippedAudioPublicationTest {
    private lateinit var folder: File
    private lateinit var provider: ClipProvider
    private lateinit var repo: MediaStoreAudioTrimRepository
    private val payload = "completed audio bytes".toByteArray()

    class ClipProvider : ContentProvider() {
        lateinit var folder: File
        var file: File? = null
        var pending = false
        var failPublish = false
        var storedSize = 0L
        override fun onCreate() = true
        override fun getType(uri: Uri) = "audio/mp4"
        override fun insert(uri: Uri, values: ContentValues?): Uri {
            pending = values?.getAsInteger(MediaStore.MediaColumns.IS_PENDING) == 1
            file = File(folder, ".pending-clip.m4a").apply { createNewFile() }
            Hooks.events += "insert"
            return Uri.parse("content://media/external/audio/media/41")
        }
        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
            Hooks.events += "open"
            return ParcelFileDescriptor.open(file!!,
                ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_TRUNCATE)
        }
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
                args: Array<out String>?, sort: String?): Cursor {
            val columns = projection?.toList()?.toTypedArray() ?: arrayOf(MediaStore.MediaColumns.DATA)
            return MatrixCursor(columns).apply {
                if (file != null && selection == null) {
                    addRow(columns.map { if (it == MediaStore.MediaColumns.DATA) file!!.absolutePath else 41L }.toTypedArray())
                }
            }
        }
        override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?): Int {
            if (values?.containsKey(MediaStore.MediaColumns.SIZE) == true) {
                storedSize = values.getAsLong(MediaStore.MediaColumns.SIZE)
                Hooks.events += "size"
            }
            if (values?.getAsInteger(MediaStore.MediaColumns.IS_PENDING) == 0) {
                if (failPublish) {
                    Hooks.events += "publish_failed"
                    throw java.io.IOException("Publishing failed")
                }
                check(pending)
                val published = File(folder, "clip.m4a")
                check(file!!.renameTo(published))
                file = published
                pending = false
                Hooks.events += "publish"
            }
            return 1
        }
        override fun delete(uri: Uri, selection: String?, args: Array<out String>?): Int {
            file?.delete(); file = null; pending = false; Hooks.events += "delete"; return 1
        }
    }

    @Before fun setup() {
        Hooks.reset()
        folder = java.nio.file.Files.createTempDirectory("clip-legal-test-").toFile()
        val context = RuntimeEnvironment.getApplication()
        provider = ClipProvider().apply { this.folder = this@ClippedAudioPublicationTest.folder }
        provider.attachInfo(context, ProviderInfo().apply { authority = "media" })
        ShadowContentResolver.registerProviderInternal("media", provider)
        repo = MediaStoreAudioTrimRepository(context)
    }
    @After fun cleanup() { folder.deleteRecursively() }
    private fun save(): Long {
        val input = File(folder, "source.tmp").apply { writeBytes(payload) }
        val method = MediaStoreAudioTrimRepository::class.java.getDeclaredMethod(
            "insertIntoMediaStore", File::class.java, String::class.java, String::class.java, String::class.java)
        method.isAccessible = true
        return method.invoke(repo, input, "clip", "m4a", "audio/mp4") as Long
    }

    @Test fun `signs completed pending bytes before publish and notifies using final renamed path`() {
        assertEquals(41L, save())
        assertArrayEquals(payload, Hooks.preparedBytes)
        assertEquals(listOf("insert", "open", "prepare", "size", "publish", "notify"), Hooks.events)
        assertFalse(provider.pending)
        assertEquals(provider.file!!.length(), provider.storedSize)
        assertEquals(File(folder, "clip.m4a").absolutePath, Hooks.publishedPath)
    }

    @Test fun `signing failure rolls back pending media without publication`() {
        Hooks.failPrepare = true
        assertThrows(InvocationTargetException::class.java) { save() }
        assertEquals(listOf("insert", "open", "prepare", "delete"), Hooks.events)
        assertNull(provider.file)
        assertNull(Hooks.publishedPath)
    }

    @Test fun `publication failure cleans up signed pending media without notifying`() {
        provider.failPublish = true
        assertThrows(InvocationTargetException::class.java) { save() }
        assertEquals(listOf("insert", "open", "prepare", "size", "publish_failed", "delete"), Hooks.events)
        assertNull(Hooks.publishedPath)
        assertNull(provider.file)
    }
}
// Leapy_tts end
