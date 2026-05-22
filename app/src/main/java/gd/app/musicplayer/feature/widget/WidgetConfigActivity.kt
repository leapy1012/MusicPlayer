package gd.app.musicplayer.feature.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import android.widget.ViewFlipper
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.applySystemBarInsets
import gd.app.musicplayer.core.common.extension.screenWidth
import gd.app.musicplayer.core.designsystem.view.SeekBar
import gd.app.musicplayer.core.datastore.WidgetConfigStore
import gd.app.musicplayer.databinding.ActivityWidgetConfigBinding
import gd.app.musicplayer.ui.common.base.BaseActivity
import gd.app.musicplayer.feature.widget.provider.WidgetPlaybackSnapshotLoader
import gd.app.musicplayer.feature.widget.provider.WidgetRenderer
import gd.app.musicplayer.feature.widget.provider.WidgetUpdateCoordinator
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class WidgetConfigActivity : BaseActivity() {

    @Inject
    lateinit var store: WidgetConfigStore

    @Inject
    lateinit var snapshotLoader: WidgetPlaybackSnapshotLoader

    @Inject
    lateinit var widgetUpdateCoordinator: WidgetUpdateCoordinator

    private lateinit var binding: ActivityWidgetConfigBinding
    private lateinit var spec: WidgetProviderSpec

    private lateinit var themeAdapter: WidgetThemeAdapter
    private lateinit var styleAdapter: WidgetStyleAdapter

    private var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    private var selectedStyle: WidgetStyleOption? = null
    private var selectedThemeOption: WidgetThemeOption? = null
    private var selectedThemeAlpha: Float = DEFAULT_THEME_ALPHA

    private var previewRoot: View? = null
    private var previewBackgroundImage: ImageView? = null
    private var previewQueueAdapter: WidgetQueueAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityWidgetConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupBaseUi()

        lifecycleScope.launch {
            loadFromIntent(intent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        setIntent(intent)

        lifecycleScope.launch {
            loadFromIntent(intent)
        }
    }

    private fun setupBaseUi() {
        binding.root.visibility = View.VISIBLE
        binding.root.applySystemBarInsets(
            binding.statusBarSpace,
            binding.widgetBottomLayout
        )

        binding.widgetBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        binding.widgetSave.setOnClickListener {
            saveAndFinish()
        }

        setupThemeRecycler()
        setupStyleRecycler()
        setupOpacity()
    }

    private suspend fun loadFromIntent(intent: Intent) {
        appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            intent.getIntExtra(
                EXTRA_APP_WIDGET_ID_LEGACY,
                AppWidgetManager.INVALID_APPWIDGET_ID
            )
        )

        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            setResult(RESULT_CANCELED)
        }

        val classify = resolveClassify(intent)

        if (classify == null) {
            finish()
            return
        }

        spec = WidgetCatalog.specForClassify(classify)

        val currentConfig = store.load(
            appWidgetId = appWidgetId,
            classify = spec.classify
        )

        selectedStyle = resolveStyleOption(
            spec = spec,
            rawStyleKey = currentConfig.styleKey
        )

        selectedThemeOption =
            WidgetCatalog.themeOption(currentConfig.themeType, currentConfig.themeIndex)

        selectedThemeAlpha = currentConfig.alpha.coerceIn(0f, 1f)

        themeAdapter.submitSelection(selectedThemeOption)
        styleAdapter.submitItems(
            items = spec.styles,
            classify = spec.classify
        )
        styleAdapter.submitSelection(selectedStyle)

        syncOpacityLabel()
        renderPreview()
        scrollSelectedItemsIntoView()
    }

    private suspend fun resolveClassify(intent: Intent): String? {
        intent.getStringExtra(EXTRA_CLASSIFY)?.let { classify ->
            return classify
        }

        intent.getStringExtra(EXTRA_CLASSIFY_LEGACY)?.let { classify ->
            return classify
        }

        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            val providerInfo = AppWidgetManager
                .getInstance(this)
                .getAppWidgetInfo(appWidgetId)

            if (providerInfo != null) {
                return WidgetCatalog.classifyForProvider(
                    Class.forName(providerInfo.provider.className)
                )
            }

            store.loadClassify(appWidgetId)?.let { classify ->
                return classify
            }
        }

        return null
    }

    private fun setupThemeRecycler() {
        themeAdapter = WidgetThemeAdapter(
            items = WidgetCatalog.themeOptions,
            applyTheme = ::applyThemeTo,
            onSelected = ::onThemeSelected
        )

        binding.widgetThemeRecycler.apply {
            layoutManager = LinearLayoutManager(
                this@WidgetConfigActivity,
                RecyclerView.HORIZONTAL,
                false
            )

            if (itemDecorationCount == 0) {
                addItemDecoration(
                    EdgeSpacingItemDecoration(
                        edge = resources.getDimensionPixelSize(
                            R.dimen.widget_config_content_margin_start
                        ),
                        spacing = resources.getDimensionPixelSize(
                            R.dimen.widget_config_item_space
                        )
                    )
                )
            }

            adapter = themeAdapter
            setHasFixedSize(true)
        }
    }

    private fun setupStyleRecycler() {
        styleAdapter = WidgetStyleAdapter(
            applyTheme = ::applyThemeTo,
            onSelected = ::onStyleSelected
        )

        binding.widgetStyleRecycler.apply {
            layoutManager = LinearLayoutManager(
                this@WidgetConfigActivity,
                RecyclerView.HORIZONTAL,
                false
            )

            if (itemDecorationCount == 0) {
                addItemDecoration(
                    EdgeSpacingItemDecoration(
                        edge = resources.getDimensionPixelSize(
                            R.dimen.widget_config_content_margin_start
                        ),
                        spacing = resources.getDimensionPixelSize(
                            R.dimen.widget_config_item_space
                        )
                    )
                )
            }

            adapter = styleAdapter
            setHasFixedSize(true)
        }
    }

    private fun setupOpacity() {
        binding.widgetOpacitySeek.setMax(OPACITY_MAX_PROGRESS)

        binding.widgetOpacitySeek.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar,
                    progress: Int,
                    fromUser: Boolean
                ) {
                    binding.widgetOpacitySeekText.text = "$progress%"

                    if (!fromUser) return

                    selectedThemeAlpha =
                        progress.toFloat() / OPACITY_MAX_PROGRESS.toFloat()

                    /*
                     * Important:
                     * Do not call renderPreview() here.
                     * The reference app only changes background alpha while dragging.
                     */
                    previewBackgroundImage?.alpha = selectedThemeAlpha
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) {
                    binding.widgetScrollView.requestDisallowInterceptTouchEvent(true)
                }

                override fun onStopTrackingTouch(seekBar: SeekBar) {
                    binding.widgetScrollView.requestDisallowInterceptTouchEvent(false)
                }
            }
        )
    }

    private fun onThemeSelected(theme: WidgetThemeOption) {
        selectedThemeOption = theme
        selectedThemeAlpha = theme.alpha.coerceIn(0f, 1f)

        themeAdapter.submitSelection(theme)

        syncOpacityLabel()
        renderPreview()

        val selectedIndex = WidgetCatalog.themeOptions.indexOf(theme)
        if (selectedIndex >= 0) {
            binding.widgetThemeRecycler.smoothScrollToPosition(selectedIndex)
        }
    }

    private fun onStyleSelected(style: WidgetStyleOption) {
        selectedStyle = style
        selectedThemeOption = WidgetCatalog.defaultThemeOption(style.styleKey)
        selectedThemeAlpha = selectedThemeOption?.alpha?.coerceIn(0f, 1f) ?: DEFAULT_THEME_ALPHA

        styleAdapter.submitSelection(style)
        themeAdapter.submitSelection(selectedThemeOption)

        syncOpacityLabel()
        renderPreview()

        val selectedIndex = spec.styles.indexOf(style)
        if (selectedIndex >= 0) {
            binding.widgetStyleRecycler.smoothScrollToPosition(selectedIndex)
        }

        val selectedThemeIndex = WidgetCatalog.themeOptionIndex(selectedThemeOption)
        if (selectedThemeIndex >= 0) {
            binding.widgetThemeRecycler.smoothScrollToPosition(selectedThemeIndex)
        }
    }

    private fun syncOpacityLabel() {
        val progress = (selectedThemeAlpha * OPACITY_MAX_PROGRESS)
            .toInt()
            .coerceIn(0, OPACITY_MAX_PROGRESS)

        binding.widgetOpacitySeek.setProgress(progress)
        binding.widgetOpacitySeekText.text = "$progress%"
    }

    private fun renderPreview() {
        val style = selectedStyle ?: return
        val theme = currentThemeSelection() ?: return

        binding.widgetPreviewContainer.removeAllViews()

        val preview = layoutInflater.inflate(
            style.layoutRes,
            binding.widgetPreviewContainer,
            false
        )

        previewRoot = preview
        previewBackgroundImage = preview.findViewById(R.id.widget_background_image)

        binding.widgetPreviewContainer.addView(
            preview,
            previewLayoutParams(spec.classify)
        )

        applyPreviewTheme(
            root = preview,
            theme = theme
        )

        preview.findViewById<ViewFlipper?>(R.id.widget_flipper_play_pause)
            ?.displayedChild = PLAYING_FLIPPER_INDEX

        /*
         * Reference behavior:
         * Any preview layout containing widget_queue gets an adapter.
         * Do not restrict this only to classify == "List".
         */
        preview.findViewById<ListView?>(R.id.widget_queue)?.let { queueView ->
            val adapter = WidgetQueueAdapter(
                context = this,
                primaryColor = previewPrimaryColor(theme),
                secondaryColor = previewSecondaryColor(theme)
            )

            previewQueueAdapter = adapter
            queueView.adapter = adapter
        }

        previewBackgroundImage =
            preview.findViewById<ImageView?>(R.id.widget_background_image)
                ?.apply {
                    setImageResource(theme.drawableRes)
                    alpha = theme.alpha
                }
    }

    private fun applyPreviewTheme(
        root: View,
        theme: WidgetThemeOption
    ) {
        val primaryColor = previewPrimaryColor(theme)
        val secondaryColor = previewSecondaryColor(theme)

        root.findViewById<TextView?>(R.id.widget_title)
            ?.setTextColor(primaryColor)

        root.findViewById<TextView?>(R.id.widget_artist)
            ?.setTextColor(secondaryColor)

        root.findViewById<TextView?>(R.id.widget_queue_info)
            ?.setTextColor(secondaryColor)

        root.findViewById<ImageView?>(R.id.widget_background_image)?.apply {
            setImageResource(theme.drawableRes)
            alpha = theme.alpha
        }

        root.findViewById<ImageView?>(R.id.widget_album_image)
            ?.setImageResource(
                WidgetCatalog.artworkStyle(
                    (selectedStyle ?: spec.styles.first()).styleKey
                ).previewRes
            )

        WIDGET_PRIMARY_ICON_IDS.forEach { id ->
            root.findViewById<ImageView?>(id)?.let { imageView ->
                ImageViewCompat.setImageTintList(
                    imageView,
                    android.content.res.ColorStateList.valueOf(primaryColor)
                )
            }
        }

        root.findViewById<ImageView?>(R.id.widget_favorite_selected)
            ?.setColorFilter(
                ContextCompat.getColor(
                    this,
                    R.color.color_theme
                )
            )

        val useDarkForeground = theme.shouldUseDarkForeground()

        val buttonBackground = if (useDarkForeground) {
            R.drawable.widget_click_bg_btn_black
        } else {
            R.drawable.widget_click_bg_btn
        }

        val settingBackground = if (useDarkForeground) {
            R.drawable.widget_click_bg_setting_black
        } else {
            R.drawable.widget_click_bg_setting
        }

        WIDGET_BUTTON_BACKGROUND_IDS.forEach { id ->
            root.findViewById<View?>(id)
                ?.setBackgroundResource(buttonBackground)
        }

        root.findViewById<View?>(R.id.widget_flipper_play_pause)
            ?.setBackgroundResource(buttonBackground)

        root.findViewById<View?>(R.id.widget_flipper_favorite)
            ?.setBackgroundResource(buttonBackground)

        root.findViewById<View?>(R.id.widget_setting)
            ?.setBackgroundResource(settingBackground)

        root.findViewById<ViewFlipper?>(R.id.widget_progress_flipper)
            ?.displayedChild = if (useDarkForeground) {
            DARK_PROGRESS_FLIPPER_INDEX
        } else {
            LIGHT_PROGRESS_FLIPPER_INDEX
        }

        root.findViewById<ViewFlipper?>(R.id.widget_flipper_play_pause)
            ?.displayedChild = PLAYING_FLIPPER_INDEX

        previewQueueAdapter?.updateColors(
            primaryColor = primaryColor,
            secondaryColor = secondaryColor
        )
    }

    private fun previewLayoutParams(classify: String): FrameLayout.LayoutParams {
        val horizontalMargin = resources.getDimensionPixelSize(R.dimen.widget_preview_margin_h)
        val fullWidth = screenWidth - horizontalMargin * 2

        val (width, heightResId) = when (classify) {
            "2*1" -> (screenWidth * 0.45f).toInt() to R.dimen.widget_2x1_height
            "3*2" -> (screenWidth * 0.75f).toInt() to R.dimen.widget_3x2_height
            "4*2" -> fullWidth to R.dimen.widget_4x2_height
            "4*3" -> fullWidth to R.dimen.widget_4x3_height
            "4*4", "List" -> resources.getDimensionPixelSize(R.dimen.widget_4x4_height) to R.dimen.widget_4x4_height
            else -> fullWidth to R.dimen.widget_4x1_height
        }

        return FrameLayout.LayoutParams(
            width,
            resources.getDimensionPixelSize(heightResId)
        ).apply {
            gravity = Gravity.CENTER
        }
    }

    private fun saveAndFinish() {
        val style = selectedStyle ?: return
        val theme = currentThemeSelection() ?: return

        val config = WidgetConfig(
            classify = spec.classify,
            styleKey = style.styleKey,
            themeType = theme.themeType,
            themeIndex = theme.index,
            alpha = theme.alpha
        )

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        lifecycleScope.launch {
            store.save(
                appWidgetId = appWidgetId,
                config = config
            )

            WidgetRenderer.updateWidgets(
                context = this@WidgetConfigActivity,
                manager = AppWidgetManager.getInstance(this@WidgetConfigActivity),
                appWidgetIds = intArrayOf(appWidgetId),
                classify = spec.classify,
                snapshot = snapshotLoader.load(),
                configs = mapOf(appWidgetId to config)
            )

            setResult(
                RESULT_OK,
                Intent()
                    .putExtra(
                        AppWidgetManager.EXTRA_APPWIDGET_ID,
                        appWidgetId
                    )
                    .putExtra(
                        EXTRA_APP_WIDGET_ID_LEGACY,
                        appWidgetId
                    )
            )

            finish()
        }
    }

    private fun scrollSelectedItemsIntoView() {
        val themeIndex = WidgetCatalog.themeOptionIndex(selectedThemeOption)

        if (themeIndex >= 0) {
            binding.widgetThemeRecycler.scrollToPosition(themeIndex)
        }

        val styleIndex = spec.styles.indexOf(selectedStyle)

        if (styleIndex >= 0) {
            binding.widgetStyleRecycler.scrollToPosition(styleIndex)
        }
    }

    private fun resolveStyleOption(
        spec: WidgetProviderSpec,
        rawStyleKey: String
    ): WidgetStyleOption {
        return WidgetCatalog.resolveStyleOption(spec, rawStyleKey)
    }

    private fun currentThemeSelection(): WidgetThemeOption? {
        return selectedThemeOption?.copy(
            alpha = selectedThemeAlpha.coerceIn(0f, 1f)
        )
    }

    private fun previewPrimaryColor(theme: WidgetThemeOption): Int {
        return if (theme.shouldUseDarkForeground()) {
            DARK_FOREGROUND_PRIMARY
        } else {
            Color.WHITE
        }
    }

    private fun previewSecondaryColor(theme: WidgetThemeOption): Int {
        return if (theme.shouldUseDarkForeground()) {
            DARK_FOREGROUND_SECONDARY
        } else {
            LIGHT_FOREGROUND_SECONDARY
        }
    }

    companion object {
        private const val EXTRA_CLASSIFY = "widget_classify"
        private const val EXTRA_CLASSIFY_LEGACY = "KEY_WIDGET_CLASSIFY"
        private const val EXTRA_APP_WIDGET_ID_LEGACY = "appWidgetId"

        private const val DEFAULT_THEME_ALPHA = 0.7f
        private const val OPACITY_MAX_PROGRESS = 100

        private const val LIGHT_PROGRESS_FLIPPER_INDEX = 0
        private const val DARK_PROGRESS_FLIPPER_INDEX = 1
        private const val PLAYING_FLIPPER_INDEX = 1

        private const val DARK_FOREGROUND_PRIMARY = -570425344
        private const val DARK_FOREGROUND_SECONDARY = -1979711488
        private const val LIGHT_FOREGROUND_SECONDARY = -1275068417

        private val WIDGET_PRIMARY_ICON_IDS = intArrayOf(
            R.id.widget_previous,
            R.id.widget_next,
            R.id.widget_play,
            R.id.widget_pause,
            R.id.widget_mode,
            R.id.widget_setting,
            R.id.widget_favorite_unselected
        )

        private val WIDGET_BUTTON_BACKGROUND_IDS = intArrayOf(
            R.id.widget_previous,
            R.id.widget_next,
            R.id.widget_mode
        )

        fun intent(
            context: Context,
            appWidgetId: Int,
            classify: String
        ): Intent {
            return Intent(
                context,
                WidgetConfigActivity::class.java
            ).apply {
                putExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    appWidgetId
                )
                putExtra(
                    EXTRA_CLASSIFY,
                    classify
                )
                putExtra(
                    EXTRA_CLASSIFY_LEGACY,
                    classify
                )
                putExtra(
                    EXTRA_APP_WIDGET_ID_LEGACY,
                    appWidgetId
                )
            }
        }
    }
}

private class EdgeSpacingItemDecoration(
    private val edge: Int,
    private val spacing: Int
) : RecyclerView.ItemDecoration() {

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        val position = parent.getChildAdapterPosition(view)
        val count = parent.adapter?.itemCount ?: 0

        if (position == RecyclerView.NO_POSITION || count == 0) return

        outRect.left =
            if (position == 0) {
                edge
            } else {
                spacing
            }

        outRect.right =
            if (position == count - 1) {
                edge
            } else {
                spacing
            }
    }
}
