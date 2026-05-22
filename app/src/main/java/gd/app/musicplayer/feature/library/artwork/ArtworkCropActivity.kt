package gd.app.musicplayer.feature.library.artwork

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import com.yalantis.ucrop.callback.BitmapCropCallback
import com.yalantis.ucrop.view.TransformImageView
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.parcelable
import gd.app.musicplayer.core.common.extension.applySystemBarInsets
import gd.app.musicplayer.core.common.util.ToastUtil
import gd.app.musicplayer.databinding.ActivityCropPhotoboxBinding
import gd.app.musicplayer.ui.common.base.BaseActivity
import java.io.File

@AndroidEntryPoint
class ArtworkCropActivity : BaseActivity() {

    private lateinit var binding: ActivityCropPhotoboxBinding
    private lateinit var outputUri: Uri
    private var cropCommitted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCropPhotoboxBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyThemeTo(binding.root)
        binding.root.applySystemBarInsets(binding.statusBarSpace)

        val sourceUri = intent.parcelable<Uri>(EXTRA_SOURCE_URI)
        val outputPath = intent.getStringExtra(EXTRA_OUTPUT_PATH)
        if (sourceUri == null || outputPath.isNullOrBlank()) {
            finishWithError(null)
            return
        }
        outputUri = Uri.fromFile(File(outputPath))

        configureCropView()
        configureActions()
        loadSource(sourceUri)
    }

    private fun configureCropView() {
        binding.cropView.cropImageView.apply {
            targetAspectRatio = 1f
            setMaxScaleMultiplier(10f)
            setImageToWrapCropBoundsAnimDuration(300L)

            // Theme backgrounds do not need very large output. Keeping the crop result smaller
            // reduces stutter with high-resolution camera images.
            setMaxResultImageSizeX(MAX_RESULT_IMAGE_SIZE)
            setMaxResultImageSizeY(MAX_RESULT_IMAGE_SIZE)
        }

        binding.cropView.overlayView.apply {
            setTargetAspectRatio(1f)
            setFreestyleCropEnabled(false)
            setShowCropGrid(false)
            setShowCropFrame(true)
        }
    }

    private fun configureActions() {
        binding.cropClose.setOnClickListener { finishCanceled() }
        binding.cropSave.setOnClickListener { cropAndSave() }
    }

    private fun loadSource(sourceUri: Uri) {
        showLoading(true)
        binding.cropView.cropImageView.setTransformImageListener(
            object : TransformImageView.TransformImageListener {
                override fun onLoadComplete() {
                    binding.cropView.alpha = 1f
                    binding.cropBlocking.visibility = View.GONE
                    showLoading(false)
                }

                override fun onLoadFailure(e: Exception) {
                    finishWithError(e)
                }

                override fun onRotate(currentAngle: Float) = Unit

                override fun onScale(currentScale: Float) = Unit
            }
        )
        runCatching {
            binding.cropView.cropImageView.setImageUri(sourceUri, outputUri)
        }.onFailure {
            finishWithError(it)
        }
    }

    private fun cropAndSave() {
        showLoading(true)
        binding.cropView.cropImageView.cropAndSaveImage(
            Bitmap.CompressFormat.JPEG,
            92,
            object : BitmapCropCallback {
                override fun onBitmapCropped(
                    resultUri: Uri,
                    offsetX: Int,
                    offsetY: Int,
                    imageWidth: Int,
                    imageHeight: Int
                ) {
                    cropCommitted = true
                    setResult(
                        RESULT_OK,
                        Intent().putExtra(RESULT_ARTWORK_PATH, resultUri.path)
                    )
                    finish()
                }

                override fun onCropFailure(t: Throwable) {
                    finishWithError(t)
                }
            }
        )
    }

    private fun showLoading(isLoading: Boolean) {
        binding.cropLoading.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.cropSave.visibility = if (isLoading) View.GONE else View.VISIBLE
        binding.cropBlocking.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    private fun finishWithError(error: Throwable?) {
        cleanupOutputFile()
        ToastUtil.show(this, R.string.album_load_failed)
        setResult(
            RESULT_CANCELED,
            Intent().putExtra(EXTRA_ERROR_MESSAGE, error?.message)
        )
        finish()
    }

    private fun finishCanceled() {
        cleanupOutputFile()
        setResult(RESULT_CANCELED)
        finish()
    }

    private fun cleanupOutputFile() {
        if (cropCommitted) return
        outputUri.path?.let { path ->
            runCatching { File(path).delete() }
        }
    }

    companion object {
        const val RESULT_ARTWORK_PATH = "result_artwork_path"
        private const val EXTRA_SOURCE_URI = "source_uri"
        private const val EXTRA_OUTPUT_PATH = "output_path"
        private const val EXTRA_ERROR_MESSAGE = "error_message"
        private const val MAX_RESULT_IMAGE_SIZE = 1440

        fun intent(context: Context, sourceUri: Uri, outputPath: String): Intent {
            return Intent(context, ArtworkCropActivity::class.java)
                .putExtra(EXTRA_SOURCE_URI, sourceUri)
                .putExtra(EXTRA_OUTPUT_PATH, outputPath)
        }
    }
}
