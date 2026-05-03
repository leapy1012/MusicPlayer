package gd.app.musicplayer.ui.feature.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import android.widget.ViewFlipper
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import gd.app.musicplayer.R
import gd.app.musicplayer.core.extension.applySystemBarInsets
import gd.app.musicplayer.core.extension.screenWidth
import gd.app.musicplayer.databinding.ActivityWidgetConfigBinding
import gd.app.musicplayer.databinding.ActivityWidgetConfigStyleItemBinding
import gd.app.musicplayer.databinding.ActivityWidgetConfigThemeItemBinding
import gd.app.musicplayer.ui.feature.widget.provider.WidgetRenderer
import gd.app.musicplayer.ui.common.base.BaseActivity
import gd.app.musicplayer.core.ui.view.SeekBar
import gd.app.musicplayer.ui.theme.applyCurrentTheme

private const val CLASSIFY_2X1 = "2*1"
private const val CLASSIFY_3X2 = "3*2"
private const val CLASSIFY_4X1 = "4*1"
private const val CLASSIFY_4X2 = "4*2"
private const val CLASSIFY_4X3 = "4*3"
private const val CLASSIFY_4X4 = "4*4"
private const val CLASSIFY_LIST = "List"

class WidgetConfigActivity : BaseActivity() {
    private lateinit var binding: ActivityWidgetConfigBinding
    private lateinit var spec: WidgetProviderSpec
    private lateinit var store: WidgetConfigStore

    private var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID
    private var selectedStyle: WidgetStyleOption? = null
    private var selectedTheme: WidgetThemeOption? = null

    companion object {
        private const val EXTRA_CLASSIFY = "widget_classify"
        private const val EXTRA_CLASSIFY_LEGACY = "KEY_WIDGET_CLASSIFY"
        private const val EXTRA_APP_WIDGET_ID_LEGACY = "appWidgetId"

        fun intent(context: Context, appWidgetId: Int, classify: String): Intent {
            return Intent(context, WidgetConfigActivity::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                putExtra(EXTRA_CLASSIFY, classify)
                putExtra(EXTRA_CLASSIFY_LEGACY, classify)
                putExtra(EXTRA_APP_WIDGET_ID_LEGACY, appWidgetId)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWidgetConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyCurrentTheme(binding.root)
        binding.root.visibility = View.VISIBLE
        binding.root.applySystemBarInsets(binding.statusBarSpace, binding.root)
        binding.widgetBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        store = WidgetConfigStore(this)
        appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            intent.getIntExtra(EXTRA_APP_WIDGET_ID_LEGACY, AppWidgetManager.INVALID_APPWIDGET_ID)
        )
        if (!handleIntent(intent)) return

        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            setResult(RESULT_CANCELED)
        }

        val currentConfig = store.load(appWidgetId, spec.classify)
        selectedStyle = resolveStyleOption(spec, currentConfig.styleKey)
        selectedTheme = WidgetCatalog.themeOption(currentConfig.themeType, currentConfig.themeIndex)

        setupThemeRecycler()
        setupStyleRecycler()
        setupOpacity()
        renderPreview()

        binding.widgetSave.setOnClickListener { saveAndFinish() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent): Boolean {
        val classify = resolveClassify(intent) ?: run {
            finish()
            return false
        }
        spec = WidgetCatalog.specForClassify(classify)
        return true
    }

    private fun resolveClassify(intent: Intent): String? {
        intent.getStringExtra(EXTRA_CLASSIFY)?.let { return it }
        intent.getStringExtra(EXTRA_CLASSIFY_LEGACY)?.let { return it }
        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            store.loadClassify(appWidgetId)?.let { return it }
            val providerInfo = AppWidgetManager.getInstance(this).getAppWidgetInfo(appWidgetId)
            if (providerInfo != null) {
                return WidgetCatalog.classifyForProvider(Class.forName(providerInfo.provider.className))
            }
        }
        return null
    }

    private fun setupThemeRecycler() {
        binding.widgetThemeRecycler.layoutManager =
            LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
        if (binding.widgetThemeRecycler.itemDecorationCount == 0) {
            binding.widgetThemeRecycler.addItemDecoration(
                EdgeSpacingItemDecoration(
                    edge = resources.getDimensionPixelSize(R.dimen.widget_config_content_margin_start),
                    spacing = resources.getDimensionPixelSize(R.dimen.widget_config_item_space)
                )
            )
        }
        lateinit var adapter: ThemeAdapter
        adapter = ThemeAdapter(
            items = WidgetCatalog.themeOptions,
            onSelected = {
                selectedTheme = it
                syncOpacityLabel()
                adapter.selected = it
                renderPreview()
            }
        )
        adapter.selected = selectedTheme
        binding.widgetThemeRecycler.adapter = adapter
    }

    private fun setupStyleRecycler() {
        binding.widgetStyleRecycler.layoutManager =
            LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
        if (binding.widgetStyleRecycler.itemDecorationCount == 0) {
            binding.widgetStyleRecycler.addItemDecoration(
                EdgeSpacingItemDecoration(
                    edge = resources.getDimensionPixelSize(R.dimen.widget_config_content_margin_start),
                    spacing = resources.getDimensionPixelSize(R.dimen.widget_config_item_space)
                )
            )
        }
        lateinit var adapter: StyleAdapter
        adapter = StyleAdapter(
            items = spec.styles,
            classify = spec.classify,
            onSelected = {
                selectedStyle = it
                adapter.selected = it
                renderPreview()
            }
        )
        adapter.selected = selectedStyle
        binding.widgetStyleRecycler.adapter = adapter
    }

    private fun setupOpacity() {
        binding.widgetOpacitySeek.setMax(100)
        binding.widgetOpacitySeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(
                seekBar: SeekBar,
                progress: Int,
                fromUser: Boolean
            ) {
                binding.widgetOpacitySeekText.text = "$progress%"
                if (fromUser) {
                    selectedTheme = selectedTheme?.copy(alpha = progress / 100f)
                    renderPreview()
                }
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                binding.widgetScrollView.requestDisallowInterceptTouchEvent(false)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                binding.widgetScrollView.requestDisallowInterceptTouchEvent(true)
            }
        })
        syncOpacityLabel()
    }

    private fun syncOpacityLabel() {
        val progress = ((selectedTheme?.alpha ?: 0.7f) * 100f).toInt().coerceIn(0, 100)
        binding.widgetOpacitySeek.setProgress(progress)
        binding.widgetOpacitySeekText.text = "$progress%"
    }

    private fun renderPreview() {
        val style = selectedStyle ?: return
        val theme = selectedTheme ?: return
        binding.widgetPreviewContainer.removeAllViews()
        val preview = layoutInflater.inflate(style.layoutRes, binding.widgetPreviewContainer, false)
        binding.widgetPreviewContainer.addView(preview, previewLayoutParams(spec.classify))
        applyPreviewTheme(preview, theme)
        if (spec.classify == CLASSIFY_LIST) {
            (preview.findViewById<ListView>(R.id.widget_queue))?.adapter =
                PreviewQueueAdapter(this, theme.drawableRes == R.drawable.widget_color_bg_012)
        }
        applyCurrentTheme(binding.root)
    }

    private fun applyPreviewTheme(root: View, theme: WidgetThemeOption) {
        val title = root.findViewById<TextView?>(R.id.widget_title)
        val artist = root.findViewById<TextView?>(R.id.widget_artist)
        val queueInfo = root.findViewById<TextView?>(R.id.widget_queue_info)
        val background = root.findViewById<ImageView?>(R.id.widget_background_image)
        val album = root.findViewById<ImageView?>(R.id.widget_album_image)
        val useDarkForeground = theme.drawableRes == R.drawable.widget_color_bg_012
        val textColor = if (useDarkForeground) Color.BLACK else Color.WHITE
        val subTextColor = if (useDarkForeground) 0x99000000.toInt() else 0xB3FFFFFF.toInt()

        title?.setTextColor(textColor)
        artist?.setTextColor(subTextColor)
        queueInfo?.setTextColor(subTextColor)
        background?.setImageResource(theme.drawableRes)
        background?.alpha = theme.alpha
        album?.setImageResource(R.drawable.widget_preview_album)

        val iconIds = intArrayOf(
            R.id.widget_previous,
            R.id.widget_next,
            R.id.widget_play,
            R.id.widget_pause,
            R.id.widget_mode,
            R.id.widget_setting,
            R.id.widget_favorite_unselected
        )
        iconIds.forEach { id ->
            root.findViewById<ImageView?>(id)?.setColorFilter(textColor)
        }
        root.findViewById<ImageView?>(R.id.widget_favorite_selected)
            ?.setColorFilter(ContextCompat.getColor(this, R.color.color_theme))

        root.findViewById<ViewFlipper?>(R.id.widget_progress_flipper)?.displayedChild =
            if (useDarkForeground) 1 else 0
        root.findViewById<View?>(R.id.widget_play)?.visibility = View.GONE
        root.findViewById<View?>(R.id.widget_pause)?.visibility = View.VISIBLE
    }

    private fun previewLayoutParams(classify: String): ViewGroup.LayoutParams {
        val width = when (classify) {
            CLASSIFY_2X1 -> (screenWidth * 0.45f).toInt()
            CLASSIFY_3X2 -> (screenWidth * 0.75f).toInt()
            CLASSIFY_4X4, CLASSIFY_LIST -> resources.getDimensionPixelSize(R.dimen.widget_4x4_height)
            else -> screenWidth - resources.getDimensionPixelSize(R.dimen.widget_preview_margin_h) * 2
        }
        val heightRes = when (classify) {
            CLASSIFY_2X1 -> R.dimen.widget_2x1_height
            CLASSIFY_3X2 -> R.dimen.widget_3x2_height
            CLASSIFY_4X2 -> R.dimen.widget_4x2_height
            CLASSIFY_4X3 -> R.dimen.widget_4x3_height
            CLASSIFY_4X4, CLASSIFY_LIST -> R.dimen.widget_4x4_height
            else -> R.dimen.widget_4x1_height
        }
        return ViewGroup.LayoutParams(width, resources.getDimensionPixelSize(heightRes))
    }

    private fun saveAndFinish() {
        val style = selectedStyle ?: return
        val theme = selectedTheme ?: return
        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            store.save(
                appWidgetId,
                WidgetConfig(
                    classify = spec.classify,
                    styleKey = style.styleKey,
                    themeType = theme.themeType,
                    themeIndex = theme.index,
                    alpha = theme.alpha
                )
            )
            WidgetRenderer.updateWidgets(
                this,
                AppWidgetManager.getInstance(this),
                intArrayOf(appWidgetId),
                spec.classify
            )
            setResult(
                RESULT_OK,
                Intent()
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    .putExtra(EXTRA_APP_WIDGET_ID_LEGACY, appWidgetId)
            )
        }
        finish()
    }

    private fun resolveStyleOption(spec: WidgetProviderSpec, rawStyleKey: String): WidgetStyleOption {
        val normalized = normalizeStyleKey(rawStyleKey)
        return spec.styles.firstOrNull { normalizeStyleKey(it.styleKey) == normalized } ?: spec.styles.first()
    }

    private fun normalizeStyleKey(styleKey: String): String {
        val key = styleKey.trim()
        if (key.equals("list", ignoreCase = true)) return "LIST"
        if (key.startsWith("3x2_", ignoreCase = true)) return key.replace("3x2_", "3X2_", ignoreCase = true)
        return key.uppercase()
    }
}

private class ThemeAdapter(
    private val items: List<WidgetThemeOption>,
    private val onSelected: (WidgetThemeOption) -> Unit
) : RecyclerView.Adapter<ThemeAdapter.ViewHolder>() {
    var selected: WidgetThemeOption? = null
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ActivityWidgetConfigThemeItemBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding, onSelected)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], items[position] == selected)
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(
        private val binding: ActivityWidgetConfigThemeItemBinding,
        private val onSelected: (WidgetThemeOption) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: WidgetThemeOption, isSelected: Boolean) {
            binding.itemImage.setImageResource(item.drawableRes)
            binding.itemImage.imageAlpha = (item.alpha * 255f).toInt()
            binding.itemSelect.visibility = if (isSelected) View.VISIBLE else View.GONE
            binding.root.setOnClickListener { onSelected(item) }
        }
    }
}

private class StyleAdapter(
    private val items: List<WidgetStyleOption>,
    private val classify: String,
    private val onSelected: (WidgetStyleOption) -> Unit
) : RecyclerView.Adapter<StyleAdapter.ViewHolder>() {
    var selected: WidgetStyleOption? = null
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ActivityWidgetConfigStyleItemBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding, onSelected)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], items[position] == selected, classify)
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(
        private val binding: ActivityWidgetConfigStyleItemBinding,
        private val onSelected: (WidgetStyleOption) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: WidgetStyleOption, isSelected: Boolean, classify: String) {
            binding.itemImage.setImageResource(item.previewRes)
            binding.itemSelect.visibility = if (isSelected) View.VISIBLE else View.GONE
            val heightRes = when (classify) {
                CLASSIFY_2X1 -> R.dimen.widget_config_style_h_2x1
                CLASSIFY_3X2 -> R.dimen.widget_config_style_h_3x2
                CLASSIFY_4X1 -> R.dimen.widget_config_style_h_4x1
                CLASSIFY_4X2 -> R.dimen.widget_config_style_h_4x2
                CLASSIFY_4X3 -> R.dimen.widget_config_style_h_4x3
                else -> R.dimen.widget_config_style_h_4x4
            }
            binding.root.layoutParams = binding.root.layoutParams.apply {
                height = binding.root.resources.getDimensionPixelSize(heightRes)
            }
            binding.root.setOnClickListener { onSelected(item) }
        }
    }
}

private class PreviewQueueAdapter(
    private val context: Context,
    private val useDarkForeground: Boolean
) : BaseAdapter() {
    private val items = listOf(
        context.getString(R.string.music) to context.getString(R.string.artist),
        context.getString(R.string.music) to context.getString(R.string.artist),
        context.getString(R.string.music) to context.getString(R.string.artist)
    )

    override fun getCount(): Int = items.size

    override fun getItem(position: Int): Pair<String, String> = items[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.widget_queue_item, parent, false)
        val holder = (view.tag as? ViewHolder) ?: ViewHolder(view).also { view.tag = it }
        holder.position.text = (position + 1).toString()
        holder.title.text = items[position].first
        holder.artist.text = items[position].second
        val mainColor = if (useDarkForeground) Color.BLACK else Color.WHITE
        val subColor = if (useDarkForeground) 0x99000000.toInt() else 0xB3FFFFFF.toInt()
        holder.position.setTextColor(subColor)
        holder.title.setTextColor(mainColor)
        holder.artist.setTextColor(subColor)
        holder.divider.visibility =
            if (position == items.lastIndex) View.GONE else View.VISIBLE
        return view
    }

    private class ViewHolder(root: View) {
        val position: TextView = root.findViewById(R.id.widget_queue_item_position)
        val title: TextView = root.findViewById(R.id.widget_queue_item_title)
        val artist: TextView = root.findViewById(R.id.widget_queue_item_artist)
        val divider: View = root.findViewById(R.id.widget_queue_item_divider)
    }
}

private class EdgeSpacingItemDecoration(
    private val edge: Int,
    private val spacing: Int
) : RecyclerView.ItemDecoration() {
    override fun getItemOffsets(
        outRect: android.graphics.Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        val position = parent.getChildAdapterPosition(view)
        val count = parent.adapter?.itemCount ?: 0
        if (position == RecyclerView.NO_POSITION || count == 0) return
        outRect.left = if (position == 0) edge else spacing
        outRect.right = if (position == count - 1) edge else spacing
    }
}
