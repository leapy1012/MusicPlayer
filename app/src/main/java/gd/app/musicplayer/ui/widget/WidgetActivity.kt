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
import gd.app.musicplayer.core.common.util.ToastUtil
import gd.app.musicplayer.core.designsystem.dialog.createMessageDialogConfig
import gd.app.musicplayer.core.designsystem.dialog.showMessageDialog
import gd.app.musicplayer.databinding.ActivityWidgetBinding
import gd.app.musicplayer.ui.common.base.BaseActivity
import gd.app.musicplayer.ui.common.base.SpacingItemDecoration

@AndroidEntryPoint
class WidgetActivity : BaseActivity() {
    private lateinit var binding: ActivityWidgetBinding
    private lateinit var addHelper: WidgetAddHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (handleConfigureIntent(intent)) {
            return
        }

        binding = ActivityWidgetBinding.inflate(layoutInflater)
        setContentView(binding.root)

        addHelper = WidgetAddHelper(
            activity = this,
            onAddSuccess = {
                ToastUtil.show(this, R.string.dlg_add_widget_success)
            },
            onManualAddRequired = {
                showManualAddDialog()
            }
        )

        binding.root.applySystemBarInsets(
            statusBarView = binding.statusBarSpace,
            bottomPaddingView = binding.root
        )

        setupToolbar()
        setupRecycler()
    }

    override fun onDestroy() {
        if (::addHelper.isInitialized) {
            addHelper.dispose()
        }

        super.onDestroy()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupRecycler() {
        binding.recyclerView.layoutManager = LinearLayoutManager(this)

        if (binding.recyclerView.itemDecorationCount == 0) {
            binding.recyclerView.addItemDecoration(
                SpacingItemDecoration.all(dpToPx(8f))
            )
        }

        binding.recyclerView.adapter = WidgetSizeAdapter(
            items = WidgetCatalog.items,
            applyTheme = { root ->
                themeEngine.apply(root)
            },
            onAddClicked = { item ->
                addHelper.requestAdd(item)
            }
        )
    }

    private fun handleConfigureIntent(intent: Intent?): Boolean {
        val appWidgetId = intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            return false
        }

        setResult(
            RESULT_OK,
            Intent().putExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                appWidgetId
            )
        )

        finish()
        return true
    }

    private fun showManualAddDialog() {
        showMessageDialog(
            createMessageDialogConfig(
                title = getString(R.string.widget),
                message =
                listOf(
                    getString(R.string.dlg_add_widget_tips_1),
                    getString(R.string.dlg_add_widget_tips_2),
                    getString(R.string.dlg_add_widget_tips_3),
                    getString(R.string.dlg_add_widget_tips_4)
                ).joinToString(separator = "\n\n"),
                positiveText = getString(android.R.string.ok)
            )
        )
    }

    companion object {
        fun start(context: Context) {
            context.startActivityCompat(
                Intent(context, WidgetActivity::class.java)
            )
        }
    }
}
