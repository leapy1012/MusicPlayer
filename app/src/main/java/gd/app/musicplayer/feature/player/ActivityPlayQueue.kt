package gd.app.musicplayer.feature.player

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.core.ui.extension.startActivityCompat
import gd.app.musicplayer.databinding.ActivityPlayQueueBinding
import gd.app.musicplayer.ui.common.base.BaseActivity

@AndroidEntryPoint
class ActivityPlayQueue : BaseActivity() {

    companion object {
        fun start(context: Context) {
            context.startActivityCompat(
                Intent(context, ActivityPlayQueue::class.java)
                    .putExtra(EXTRA_SCREEN_MODE, SCREEN_MODE_PLAYER)
            )
        }

        fun startQueue(context: Context) {
            context.startActivityCompat(
                Intent(context, ActivityPlayQueue::class.java)
                    .putExtra(EXTRA_SCREEN_MODE, SCREEN_MODE_QUEUE)
            )
        }

        private const val EXTRA_SCREEN_MODE = "screen_mode"
        private const val SCREEN_MODE_PLAYER = "player"
        private const val SCREEN_MODE_QUEUE = "queue"
    }
    private lateinit var binding : ActivityPlayQueueBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityPlayQueueBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initViews(savedInstanceState)
    }

    

    private fun initViews(savedInstanceState: Bundle?) {
        binding.mainBackground.setBackgroundResource(R.drawable.th_music_large)

        if (savedInstanceState != null) return
        if (intent.getStringExtra(EXTRA_SCREEN_MODE) == SCREEN_MODE_QUEUE) {
            supportFragmentManager.beginTransaction()
                .replace(
                    binding.mainFragmentContainer.id,
                    PlaybackQueueFragment(),
                    PlaybackQueueFragment::class.java.simpleName
                )
                .replace(
                    binding.mainFragmentBanner.id,
                    QueueControlFragment(),
                    QueueControlFragment::class.java.simpleName
                )
                .commitNow()
        } else {
            binding.mainFragmentBanner.visibility = View.GONE
            supportFragmentManager.beginTransaction()
                .replace(
                    binding.mainFragmentContainer.id,
                    FragmentPlayContent(),
                    FragmentPlayContent::class.java.simpleName
                )
                .commitNow()
        }
    }
}
