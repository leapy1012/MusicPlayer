package gd.app.musicplayer.ui.theme

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.applyCachedStatusBarHeight
import gd.app.musicplayer.core.common.extension.screenHeight
import gd.app.musicplayer.core.common.extension.screenWidth
import gd.app.musicplayer.core.common.extension.startActivityCompat
import gd.app.musicplayer.core.designsystem.theme.ThemeManager
import gd.app.musicplayer.core.designsystem.theme.ThemePalette
import gd.app.musicplayer.core.designsystem.theme.accentColor
import gd.app.musicplayer.core.designsystem.theme.headerSubtitleColor
import gd.app.musicplayer.core.designsystem.theme.headerTitleColor
import gd.app.musicplayer.databinding.ActivityThemeBinding
import gd.app.musicplayer.ui.common.base.BaseActivity
import gd.app.musicplayer.ui.common.base.setupEdgeToEdgeToolbar
import gd.app.musicplayer.feature.library.artwork.ArtworkCropActivity
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ThemeActivity : BaseActivity() {
    private val viewModel: ThemeViewModel by viewModels()

    private lateinit var binding: ActivityThemeBinding
    private lateinit var pagerAdapter: ThemePagerAdapter
    private var tabMediator: TabLayoutMediator? = null
    /** Defer grid/tab population until enter animation ends (original fills after async s1). */
    private var enterAnimationComplete = false
    private var pendingUiState: ThemeUiState? = null

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
        private const val ENTER_ANIMATION_FALLBACK_MS = 450L

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
        // Original attaches an empty ViewPager in y0; catalog fills async after s1.
        // Dream's ViewPager2 creates fragments immediately — defer until enter animation
        // ends so fragment inflate / DiffUtil / Glide do not fight music_activity_in.
        observeUiState()
        observeEffects()
        scheduleEnterAnimationFallback()
    }

    private fun scheduleEnterAnimationFallback() {
        window.decorView.postDelayed({
            if (!enterAnimationComplete) {
                onEnterAnimationComplete()
            }
        }, ENTER_ANIMATION_FALLBACK_MS)
    }

    private fun ensurePagerReady() {
        if (this::pagerAdapter.isInitialized) return
        setupPager()
    }

    private fun setupInsets() {
        // Original ActivityTheme.y0: w0.h(status_bar_space) only — sync cached height.
        binding.statusBarSpace.applyCachedStatusBarHeight()
    }

    private fun setupToolbar() {
        setupEdgeToEdgeToolbar(
            root = binding.root,
            statusBarView = binding.statusBarSpace,
            bottomPaddingView = binding.root,
            toolbar = binding.toolbar,
            titleRes = R.string.theme,
        )
        binding.themeEdit.setOnClickListener { ThemeEditActivity.start(this) }
        updateEditVisibility(themeEngine.currentTheme().getThemeType())
        applyHeaderChrome()
    }

    private fun setupAccentColorDialog() {
        binding.themeAccentColor.setOnClickListener {
            val fallbackColor = themeRepo.getAccentColor()
            viewModel.onAccentColorClicked(fallbackColor)
        }
        binding.themeAccentRing.setOnClickListener {
            binding.themeAccentColor.performClick()
        }

        supportFragmentManager.setFragmentResultListener(
            SelectAccentColorDialog.RESULT_KEY,
            this
        ) { _, result ->
            if (result.containsKey(SelectAccentColorDialog.RESULT_COLOR)) {
                applyThemeTo(binding.root)
                applyHeaderChrome()
            }
        }
    }

    override fun onThemeChanged(palette: ThemePalette?) {
        super.onThemeChanged(palette)
        if (::binding.isInitialized) {
            applyHeaderChrome()
        }
    }

    /**
     * Tab labels and header action icons sit outside [Toolbar], so the toolbar binder
     * never reaches them. Light → dark neutrals; dark / pictured → light colors.
     */
    private fun applyHeaderChrome() {
        val palette = themeEngine.currentTheme()
        val isLight = palette.getThemeType() == ThemeManager.THEME_TYPE_LIGHT
        val accent = themeRepo.getAccentColor().takeIf { it != 0 } ?: palette.accentColor

        val unselectedTabColor = if (isLight) {
            resolveAttrColor(
                com.coui.appcompat.R.attr.couiColorSecondNeutral,
                palette.headerSubtitleColor,
            )
        } else {
            palette.headerSubtitleColor
        }
        binding.tabLayout.setTabTextColors(unselectedTabColor, accent)
        binding.tabLayout.setSelectedTabIndicatorColor(accent)

        val iconColor = if (isLight) {
            resolveAttrColor(
                com.coui.appcompat.R.attr.couiColorPrimaryNeutral,
                palette.headerTitleColor,
            )
        } else {
            palette.headerTitleColor
        }
        binding.themeEdit.imageTintList = ColorStateList.valueOf(iconColor)

        val density = resources.displayMetrics.density
        binding.themeAccentRing.setImageDrawable(
            GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(ColorUtils.setAlphaComponent(iconColor, if (isLight) 0x1A else 0x33))
            }
        )
        binding.themeAccentColor.setImageDrawable(
            GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(accent)
                // Keep a thin rim so the swatch reads on light headers when accent is pale.
                setStroke((1.5f * density).toInt().coerceAtLeast(1), iconColor)
            }
        )
    }

    private fun resolveAttrColor(attr: Int, fallback: Int): Int {
        val typed = obtainStyledAttributes(intArrayOf(attr))
        val color = typed.getColor(0, fallback)
        typed.recycle()
        return color
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
                            val cropWidth = screenWidth
                            val cropHeight = screenHeight
                            cropImageLauncher.launch(
                                ArtworkCropActivity.intent(
                                    this@ThemeActivity,
                                    effect.sourceUri,
                                    effect.outputPath,
                                    aspectRatioX = cropWidth.toFloat(),
                                    aspectRatioY = cropHeight.toFloat(),
                                    maxResultSizeX = cropWidth,
                                    maxResultSizeY = cropHeight
                                )
                            )
                        }

                        ThemeEffect.OpenImagePicker -> {
                            pickImageLauncher.launch("image/*")
                        }

                        ThemeEffect.ApplyTheme -> {
                            applyThemeTo(binding.root)
                            applyHeaderChrome()
                        }
                    }
                }
            }
        }
    }

    private fun render(state: ThemeUiState) {
        if (!enterAnimationComplete) {
            pendingUiState = state
            return
        }
        applyUiState(state)
    }

    private fun applyUiState(state: ThemeUiState) {
        ensurePagerReady()
        pagerAdapter.submitList(state.themes)
        updateTabTitles()
        updateEditVisibility(state.settings?.themeType)

        if (binding.viewPager.currentItem != state.selectedTabIndex &&
            state.selectedTabIndex in 0 until pagerAdapter.itemCount
        ) {
            binding.viewPager.setCurrentItem(state.selectedTabIndex, false)
        }
    }

    private fun updateEditVisibility(themeType: Int?) {
        // Blur / overlay editing only applies to wallpaper (picture) themes.
        binding.themeEdit.visibility =
            if (themeType == ThemeManager.THEME_TYPE_PICTURE) View.VISIBLE else View.GONE
    }

    override fun onEnterAnimationComplete() {
        super.onEnterAnimationComplete()
        if (enterAnimationComplete) return
        enterAnimationComplete = true
        ensurePagerReady()
        pendingUiState?.let(::applyUiState)
        pendingUiState = null
    }

    private fun updateTabTitles() {
        if (!this::pagerAdapter.isInitialized) return
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

