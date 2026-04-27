package gd.app.musicplayer.feature.drivemode

import android.content.Context
import android.content.Intent
import android.os.Bundle
import gd.app.musicplayer.core.ui.extension.startActivityCompat
import gd.app.musicplayer.databinding.ActivityDriveRemindBinding
import gd.app.musicplayer.ui.common.base.BaseActivity
import gd.app.musicplayer.util.PreferenceUtil

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
            PreferenceUtil.getInstance(this).setDriveWarningEnabled(false)
            DriveModeActivity.openDirect(this)
            finish()
        }
    }
}
