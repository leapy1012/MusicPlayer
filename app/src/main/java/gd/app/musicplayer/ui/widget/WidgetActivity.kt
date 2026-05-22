package gd.app.musicplayer.ui.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.applySystemBarInsets
import gd.app.musicplayer.core.common.extension.dpToPx
import gd.app.musicplayer.core.common.extension.startActivityCompat
import gd.app.musicplayer.databinding.ActivityWidgetBinding
import gd.app.musicplayer.ui.common.base.BaseActivity
import gd.app.musicplayer.ui.common.base.SpacingItemDecoration

@AndroidEntryPoint
class WidgetActivity : BaseActivity() {

    private lateinit var binding: ActivityWidgetBinding

    private var addHelper: WidgetAddHelper? = null

    private val widgetAdapter by lazy {
        WidgetSizeAdapter(
            items = WidgetCatalog.items,
            applyTheme = themeEngine::apply,
            currentTheme = themeEngine::currentTheme,
            onAddClicked = ::requestAddWidget
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (finishIfWidgetConfigureRequest(intent)) return

        binding = ActivityWidgetBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWidgetAddHelper()
        setupSystemBars()
        setupToolbar()
        setupWidgetList()
    }

    override fun onDestroy() {
        addHelper?.dispose()
        addHelper = null
        super.onDestroy()
    }

    private fun setupWidgetAddHelper() {
        addHelper = WidgetAddHelper(
            activity = this,
            onManualAddRequired = ::showManualAddDialog
        ).also { it.register() }
    }

    private fun setupSystemBars() = with(binding) {
        root.applySystemBarInsets(
            statusBarView = statusBarSpace,
            bottomPaddingView = recyclerView
        )
    }

    private fun setupToolbar() = with(binding.toolbar) {
        setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupWidgetList() = with(binding.recyclerView) {
        layoutManager = LinearLayoutManager(this@WidgetActivity)
        adapter = widgetAdapter

        if (itemDecorationCount == 0) {
            addItemDecoration(
                SpacingItemDecoration.all(dpToPx(WIDGET_ITEM_SPACING_DP))
            )
        }
    }

    private fun requestAddWidget(item: WidgetProviderSpec) {
        addHelper?.requestAdd(item)
    }

    private fun finishIfWidgetConfigureRequest(intent: Intent?): Boolean {
        val appWidgetId = intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            return false
        }

        setResult(
            RESULT_OK,
            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        )

        finish()
        return true
    }

    private fun showManualAddDialog() {
        WidgetManualAddDialogFragment.show(supportFragmentManager)
    }

    companion object {
        private const val WIDGET_ITEM_SPACING_DP = 8f

        fun start(context: Context) {
            context.startActivityCompat(
                Intent(context, WidgetActivity::class.java)
            )
        }
    }
}
