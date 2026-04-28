package gd.app.musicplayer.ui.feature.drivemode

import android.content.Context
import android.content.Intent
import android.os.Bundle
import gd.app.musicplayer.core.extension.appDependencies
import gd.app.musicplayer.core.extension.startActivityCompat
import gd.app.musicplayer.databinding.ActivityDriveRemindBinding
import gd.app.musicplayer.ui.common.base.BaseActivity

class DriveRemindActivity : BaseActivity() {

    private lateinit var binding: ActivityDriveRemindBinding

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
            appDependencies.preferenceUtil.setDriveWarningEnabled(false)
            DriveModeActivity.openDirect(this)
            finish()
        }
    }
}
