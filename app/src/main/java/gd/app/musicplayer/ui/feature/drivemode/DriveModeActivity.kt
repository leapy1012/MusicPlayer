package gd.app.musicplayer.ui.feature.drivemode

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.commit
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.core.extension.startActivityCompat
import gd.app.musicplayer.databinding.ActivityDriveModeBinding
import gd.app.musicplayer.ui.common.base.BaseActivity

@AndroidEntryPoint
class DriveModeActivity : BaseActivity() {

    private lateinit var binding: ActivityDriveModeBinding

    companion object {
        fun openDirect(context: Context) {
            context.startActivityCompat(
                Intent(context, DriveModeActivity::class.java)
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityDriveModeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                replace(
                    binding.mainFragmentContainer.id,
                    DriveModeFragment()
                )
            }
        }
    }
}