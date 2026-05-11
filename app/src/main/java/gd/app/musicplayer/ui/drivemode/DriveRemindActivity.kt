package gd.app.musicplayer.ui.drivemode

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.core.common.extension.startActivityCompat
import gd.app.musicplayer.data.local.preference.DrivePreferenceStore
import gd.app.musicplayer.databinding.ActivityDriveRemindBinding
import gd.app.musicplayer.ui.common.base.BaseActivity
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class DriveRemindActivity : BaseActivity() {

    private lateinit var binding: ActivityDriveRemindBinding
    @Inject lateinit var drivePreferenceStore: DrivePreferenceStore

    companion object {
        fun start(context: Context) {
            context.startActivityCompat(Intent(context, DriveRemindActivity::class.java))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityDriveRemindBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.driveWarningCancel.setOnClickListener {
            finish()
        }
        binding.driveWarningConfirm.setOnClickListener {
            lifecycleScope.launch {
                drivePreferenceStore.setDriveWarningEnabled(false)
                DriveModeActivity.openDirect(this@DriveRemindActivity)
                finish()
            }
        }
    }
}
