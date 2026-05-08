package gd.app.musicplayer.ui.feature.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.core.extension.applySystemBarInsets
import gd.app.musicplayer.core.extension.dpToPx
import gd.app.musicplayer.core.extension.startActivityCompat
import gd.app.musicplayer.core.util.ToastUtil
import gd.app.musicplayer.databinding.ActivityWidgetBinding
import gd.app.musicplayer.databinding.ActivityWidgetItemBinding
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

        binding.recyclerView.adapter = WidgetAdapter(
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
        AlertDialog.Builder(this)
            .setTitle(R.string.widget)
            .setMessage(
                listOf(
                    getString(R.string.dlg_add_widget_tips_1),
                    getString(R.string.dlg_add_widget_tips_2),
                    getString(R.string.dlg_add_widget_tips_3),
                    getString(R.string.dlg_add_widget_tips_4)
                ).joinToString(separator = "\n\n")
            )
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    companion object {
        fun start(context: Context) {
            context.startActivityCompat(
                Intent(context, WidgetActivity::class.java)
            )
        }
    }
}

private class WidgetAdapter(
    private val items: List<WidgetProviderSpec>,
    private val applyTheme: (View) -> Unit,
    private val onAddClicked: (WidgetProviderSpec) -> Unit
) : RecyclerView.Adapter<WidgetAdapter.ViewHolder>() {

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {
        val binding = ActivityWidgetItemBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )

        applyTheme(binding.root)

        return ViewHolder(
            binding = binding,
            onAddClicked = onAddClicked
        )
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int
    ) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int {
        return items.size
    }

    class ViewHolder(
        private val binding: ActivityWidgetItemBinding,
        private val onAddClicked: (WidgetProviderSpec) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: WidgetProviderSpec) = with(binding) {
            itemTitle.setText(item.titleRes)
            itemSize.text = root.context.getString(
                R.string.size
            ) + ": " + item.classify

            itemImage.setImageResource(item.previewRes)

            root.setOnClickListener {
                onAddClicked(item)
            }

            itemAdd.setOnClickListener {
                onAddClicked(item)
            }
        }
    }
}