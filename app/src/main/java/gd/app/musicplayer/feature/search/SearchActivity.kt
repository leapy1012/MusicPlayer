package gd.app.musicplayer.feature.search

import android.content.Context
import android.content.Intent
import android.os.Bundle
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.databinding.ActivitySearchBinding
import gd.app.musicplayer.ui.common.base.BaseActivity

@AndroidEntryPoint
class SearchActivity : BaseActivity() {

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, SearchActivity::class.java))
        }
    }

    private lateinit var binding: ActivitySearchBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(binding.mainFragmentContainer.id, SearchFragment())
                .commit()
        }
    }

    

}
