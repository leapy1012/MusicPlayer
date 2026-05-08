package gd.app.musicplayer.ui.theme

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.core.extension.applySystemBarInsets
import gd.app.musicplayer.core.extension.startActivityCompat
import gd.app.musicplayer.data.repository.ThemeRepo
import gd.app.musicplayer.databinding.ActivityThemeBinding
import gd.app.musicplayer.ui.common.base.BaseActivity
import gd.app.musicplayer.ui.feature.library.ArtworkCropActivity
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ThemeActivity : BaseActivity() {
    private val viewModel: ThemeViewModel by viewModels()

    private lateinit var binding: ActivityThemeBinding
    private lateinit var pagerAdapter: ThemePagerAdapter
    private var tabMediator: TabLayoutMediator? = null

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri ?: return@registerForActivityResult
            viewModel.onCustomThemeImagePicked(uri)
        }

    private val cropImageLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val imagePath = result.data
                ?.getStringExtra(ArtworkCropActivity.RESULT_ARTWORK_PATH)
                ?: return@registerForActivityResult

            viewModel.onThemeImageCropped(imagePath)
        }

    companion object {
        fun start(context: Context) {
            context.startActivityCompat(Intent(context, ThemeActivity::class.java))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityThemeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupInsets()
        setupToolbar()
        setupAccentColorDialog()
        setupPager()
        observeUiState()
        observeEffects()
    }

    private fun setupInsets() {
        binding.root.applySystemBarInsets(
            statusBarView = binding.statusBarSpace,
            bottomPaddingView = binding.viewPager
        )
    }

    private fun setupToolbar() {
        binding.themeBack.setOnClickListener { finish() }
        binding.themeEdit.setOnClickListener { ThemeEditActivity.start(this) }
    }

    private fun setupAccentColorDialog() {
        binding.themeAccentColor.setOnClickListener {
            val fallbackColor = themeRepo.getAccentColor()
            viewModel.onAccentColorClicked(fallbackColor)
        }

        supportFragmentManager.setFragmentResultListener(
            SelectAccentColorDialog.RESULT_KEY,
            this
        ) { _, result ->
            if (result.containsKey(SelectAccentColorDialog.RESULT_COLOR)) {
                applyThemeTo(binding.root)
            }
        }
    }

    private fun setupPager() {
        pagerAdapter = ThemePagerAdapter(this)
        binding.viewPager.adapter = pagerAdapter

        tabMediator = TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = pagerAdapter.titleFor(position)
        }.also { it.attach() }

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                viewModel.onTabSelected(tab.position)
            }

            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })
    }

    private fun observeUiState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    private fun observeEffects() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.effects.collect { effect ->
                    when (effect) {
                        is ThemeEffect.OpenAccentColorDialog -> {
                            SelectAccentColorDialog.newInstance(effect.currentColor)
                                .show(supportFragmentManager, SelectAccentColorDialog.TAG)
                        }

                        is ThemeEffect.OpenCropper -> {
                            cropImageLauncher.launch(
                                ArtworkCropActivity.intent(
                                    this@ThemeActivity,
                                    effect.sourceUri,
                                    effect.outputPath
                                )
                            )
                        }

                        ThemeEffect.OpenImagePicker -> {
                            pickImageLauncher.launch("image/*")
                        }

                        ThemeEffect.ApplyTheme -> {
                            applyThemeTo(binding.root)
                        }
                    }
                }
            }
        }
    }

    private fun render(state: ThemeUiState) {
        pagerAdapter.submitList(state.themes)
        updateTabTitles()

        if (binding.viewPager.currentItem != state.selectedTabIndex &&
            state.selectedTabIndex in 0 until pagerAdapter.itemCount
        ) {
            binding.viewPager.setCurrentItem(state.selectedTabIndex, false)
        }
    }

    private fun updateTabTitles() {
        for (index in 0 until binding.tabLayout.tabCount) {
            binding.tabLayout.getTabAt(index)?.text = pagerAdapter.titleFor(index)
        }
    }

    fun openCustomThemePicker() {
        viewModel.onPickCustomThemeClicked()
    }

    override fun onDestroy() {
        tabMediator?.detach()
        tabMediator = null
        super.onDestroy()
    }
}
