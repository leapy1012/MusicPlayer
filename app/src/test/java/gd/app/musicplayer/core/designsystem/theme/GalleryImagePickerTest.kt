package gd.app.musicplayer.core.designsystem.theme

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.net.Uri
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import gd.app.musicplayer.core.common.GalleryImagePicker
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, manifest = Config.NONE)
class GalleryImagePickerTest {
    @Test fun choosesGalleryWithoutGoingThroughGetContentTakeover() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val expected = Intent(Intent.ACTION_PICK)
            .setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*")
            .setPackage(GalleryImagePicker.GALLERY_PACKAGE)
        shadowOf(context.packageManager).addResolveInfoForIntent(expected, ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = GalleryImagePicker.GALLERY_PACKAGE
                name = "gallery.SelectionActivity"
            }
        })
        val actual = GalleryImagePicker().createIntent(context, "image/*")
        assertEquals(Intent.ACTION_PICK, actual.action)
        assertEquals(GalleryImagePicker.GALLERY_PACKAGE, actual.`package`)
        assertEquals("image/*", actual.type)
        assertEquals(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, actual.data)
    }

    @Test fun fallsBackToOpenableContentIfGalleryIsNotInstalled() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val intent = GalleryImagePicker().createIntent(context, "image/*")
        assertEquals(Intent.ACTION_GET_CONTENT, intent.action)
        assertTrue(intent.hasCategory(Intent.CATEGORY_OPENABLE))
        assertNull(intent.`package`)
    }

    @Test fun returnsSelectedContentUriAndHonorsCancellation() {
        val uri = Uri.parse("content://media/external/images/media/5")
        val picker = GalleryImagePicker()
        assertEquals(uri, picker.parseResult(Activity.RESULT_OK, Intent().setData(uri)))
        assertNull(picker.parseResult(Activity.RESULT_CANCELED, Intent().setData(uri)))
    }
}
