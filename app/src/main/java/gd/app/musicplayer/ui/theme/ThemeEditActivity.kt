package gd.app.musicplayer.ui.theme

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.yalantis.ucrop.UCrop
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.core.extension.applySystemBarInsets
import gd.app.musicplayer.core.extension.startActivityCompat
import gd.app.musicplayer.core.theme.ThemeBitmapLoader
import gd.app.musicplayer.core.ui.drawable.DrawableUtil
import gd.app.musicplayer.databinding.ActivityThemeEditBinding
import gd.app.musicplayer.ui.common.base.BaseActivity
import gd.app.musicplayer.ui.player.BottomMiniPlayerFragment
import gd.app.musicplayer.ui.player.BottomPlayerFragment
import gd.app.musicplayer.core.ui.view.SeekBar
import gd.app.musicplayer.data.local.preference.ThemeSettingPreferenceStore
import gd.app.musicplayer.ui.feature.library.ArtworkCropActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class ThemeEditActivity : BaseActivity() {

    @Inject
    lateinit var themeBitmapLoader: ThemeBitmapLoader

    @Inject lateinit var themeSettingPreferenceStore: ThemeSettingPreferenceStore

    private val viewModel: ThemeEditViewModel by viewModels()
    private lateinit var binding: ActivityThemeEditBinding

    private var previewJob: Job? = null
    private var latestPreviewToken = 0L
    private var savedImageName: String? = null

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri == null) return@registerForActivityResult
            val outputFile = ThemeBackgroundStore.createDraftBackgroundFile(this)
            cropImageLauncher.launch(
                ArtworkCropActivity.intent(this, uri, outputFile.absolutePath)
            )
        }

    private val cropImageLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val imagePath = result.data?.getStringExtra(ArtworkCropActivity.RESULT_ARTWORK_PATH)
                ?: return@registerForActivityResult
            viewModel.onImageChanged(imagePath)
        }

    private fun openCrop(sourceUri: Uri, destinationUri: Uri) {
        UCrop.of(sourceUri, destinationUri)
            .withAspectRatio(16f, 9f)
            .withMaxResultSize(2048, 2048)
            .start(this, cropImageLauncher)
    }

    private val seekBarListener = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
            if (!fromUser) return
            when (seekBar) {
                binding.imageEditAlpha -> viewModel.onOverlayAlphaChanged(progress)
                binding.imageEditBlur -> viewModel.onBlurChanged(progress)
            }
        }

        override fun onStartTrackingTouch(seekBar: SeekBar) = Unit

        override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
    }

    companion object {
        fun start(context: Context) {
            context.startActivityCompat(Intent(context, ThemeEditActivity::class.java))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityThemeEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        lifecycleScope.launch {
            val settings = themeSettingPreferenceStore.getSettingsSnapshot()
            savedImageName = settings.imageName
        }

        setupInsets()
        setupViews()
        setupActions()
        attachPreviewFragments(savedInstanceState)
        observeViewModel()
    }

    override fun onDestroy() {
        previewJob?.cancel()
        cleanupTransientDraft()
        super.onDestroy()
    }

    private fun setupInsets() {
        binding.root.applySystemBarInsets(statusBarView = binding.statusBarSpace)
    }

    private fun setupViews() {
        binding.scaleRelativeLayout.setInterceptTouchEvent(true)
        binding.imageEditAlpha.apply {
            setMax(255)
            setOnSeekBarChangeListener(seekBarListener)
        }
        binding.imageEditBlur.apply {
            setMax(ThemeEditViewModel.MAX_BLUR_RADIUS)
            setOnSeekBarChangeListener(seekBarListener)
        }
    }

    private fun setupActions() {
        binding.cropImageClose.setOnClickListener { finish() }
        binding.cropImageRestore.setOnClickListener { viewModel.restoreDefaults() }
        binding.cropImageSave.setOnClickListener { saveTheme() }
        binding.imageEditChangePicture.setOnClickListener { pickImageLauncher.launch("image/*") }
    }

    private fun attachPreviewFragments(savedInstanceState: Bundle?) {
        if (savedInstanceState != null) return

        supportFragmentManager.beginTransaction()
            .replace(
                R.id.main_fragment_container,
                BottomPlayerFragment(),
                BottomPlayerFragment::class.java.simpleName
            )
            .replace(
                R.id.main_bottom_control_container,
                BottomMiniPlayerFragment(),
                BottomMiniPlayerFragment::class.java.simpleName
            )
            .commitNowAllowingStateLoss()
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.imageEditAlpha.setProgressInner(Color.alpha(state.overlayColor))
                    binding.imageEditBlur.setProgressInner(state.blur)
                    binding.skinImageView.setMaskColor(state.overlayColor)
                    binding.previewImageView.setMaskColor(state.overlayColor)
                    renderPreview(state.imageName, state.blur)
                }
            }
        }
    }

    private fun renderPreview(imageName: String, blur: Int) {
        val token = System.currentTimeMillis().also { latestPreviewToken = it }
        previewJob?.cancel()
        previewJob = lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.Default) {
                themeBitmapLoader.loadBitmap(
                    context = this@ThemeEditActivity,
                    imageName = imageName,
                    blurRadius = blur
                )
            }
            if (latestPreviewToken != token || bitmap == null) return@launch
            binding.skinImageView.setImageBitmap(bitmap)
            binding.previewImageView.setImageBitmap(bitmap)
        }
    }

    private fun saveTheme() {
        lifecycleScope.launch {
            val state = viewModel.uiState.value
            val resolvedImageName = commitDraftImageIfNeeded(state.imageName)

            viewModel.onImageChanged(resolvedImageName)
            viewModel.save()

            savedImageName = resolvedImageName
            finish()
        }
    }

    private suspend fun commitDraftImageIfNeeded(imageName: String): String {
        if (!ThemeBackgroundStore.isManagedThemePath(this, imageName)) {
            return imageName
        }

        val draftFile = File(imageName)
        if (!draftFile.exists()) {
            return savedImageName ?: imageName
        }
        if (!ThemeBackgroundStore.isDraftThemePath(this, imageName)) {
            return draftFile.absolutePath
        }

        val savedFile = ThemeBackgroundStore.createSavedBackgroundFile(this)
        savedFile.parentFile?.mkdirs()
        runCatching {
            if (!draftFile.renameTo(savedFile)) {
                draftFile.copyTo(savedFile, overwrite = true)
                draftFile.delete()
            }
        }

        themeSettingPreferenceStore.addThemeImageUri(savedFile.absolutePath)

        return savedFile.absolutePath
    }

    private fun cleanupTransientDraft() {
        val draftImageName = viewModel.uiState.value.imageName
        val persistedImageName = savedImageName ?: return

        if (draftImageName == persistedImageName) return
        if (!ThemeBackgroundStore.isDraftThemePath(this, draftImageName)) return

        runCatching {
            File(draftImageName).delete()
        }
    }
}
