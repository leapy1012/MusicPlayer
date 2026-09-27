package gd.app.musicplayer.feature.setting

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.recyclerview.widget.COUIRecyclerView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.COUIDividerAppBarLayout
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.startActivityCompat
import gd.app.musicplayer.databinding.ActivitySettingShellBinding
import gd.app.musicplayer.ui.common.base.BaseActivity
import gd.app.musicplayer.ui.common.base.setupEdgeToEdgeToolbar

/**
 * Settings host matching DuraSpeed / OPPO SettingsBaseFragment:
 * CoordinatorLayout + [COUIDividerAppBarLayout] + ScrollingViewBehavior content.
 * Chrome tokens come from AppTheme → Theme.COUI.Main.Light/Dark.
 */
@AndroidEntryPoint
class SettingActivity : BaseActivity() {

    private val viewModel: SettingsViewModel by viewModels()

    private var dividerAppBar: COUIDividerAppBarLayout? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Warm DataStore while shell paints.
        viewModel.uiState

        val shellBinding = ActivitySettingShellBinding.inflate(layoutInflater)
        dividerAppBar = shellBinding.root.findViewById(R.id.abl)
        setContentView(shellBinding.root)
        setupEdgeToEdgeToolbar(
            root = shellBinding.skinLayout,
            statusBarView = shellBinding.statusBarSpace,
            bottomPaddingView = shellBinding.skinLayout,
            toolbar = shellBinding.toolbar,
            titleRes = R.string.settings,
        )

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settings_body_host, SettingsPreferenceFragment())
                .commit()
        }
    }

    /**
     * DuraSpeed contract: nested scroll on + spring enable + [COUIDividerAppBarLayout.bindRecyclerView].
     */
    fun bindCouiDivider(list: RecyclerView?) {
        val abl = dividerAppBar ?: return
        if (list == null) return

        list.isVerticalScrollBarEnabled = false
        list.isNestedScrollingEnabled = true
        list.clipToPadding = false
        list.overScrollMode = View.OVER_SCROLL_ALWAYS
        list.setBackgroundResource(com.coui.appcompat.R.drawable.coui_list_preference_bg)

        if (list is COUIRecyclerView) {
            list.setEnablePointerDownAction(false)
            list.setOverScrollEnable(true)
        }

        abl.bindRecyclerView(list)
    }

    fun requestSettingsNotificationPermission() {
        requestNotificationPermission()
    }

    override fun onNotificationPermissionResult() {
        super.onNotificationPermissionResult()
        (supportFragmentManager.findFragmentById(R.id.settings_body_host)
            as? SettingsPreferenceFragment)?.onNotificationPermissionResult()
    }

    companion object {
        fun start(context: Context) {
            context.startActivityCompat(Intent(context, SettingActivity::class.java))
        }
    }
}
