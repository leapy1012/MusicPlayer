package gd.app.musicplayer.core.common

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.result.contract.ActivityResultContract

/** Gallery owns theme/artwork selection; GET_CONTENT is intercepted by MediaProvider on Android 14. */
class GalleryImagePicker : ActivityResultContract<String, Uri?>() {
    override fun createIntent(context: Context, input: String): Intent {
        val gallery = Intent(Intent.ACTION_PICK)
            .setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, input)
            .setPackage(GALLERY_PACKAGE)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return if (gallery.resolveActivity(context.packageManager) != null) gallery
        else Intent(Intent.ACTION_GET_CONTENT).addCategory(Intent.CATEGORY_OPENABLE).setType(input)
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        if (resultCode == Activity.RESULT_OK) intent?.data else null

    companion object { const val GALLERY_PACKAGE = "com.android.gallery" }
}
