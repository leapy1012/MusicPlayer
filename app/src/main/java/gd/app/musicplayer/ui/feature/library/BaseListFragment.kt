package gd.app.musicplayer.ui.feature.library

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import gd.app.musicplayer.data.model.MusicSet
import gd.app.musicplayer.databinding.LayoutRecyclerviewBinding
import gd.app.musicplayer.ui.common.base.SpacingItemDecoration
import gd.app.musicplayer.ui.common.base.ViewBindingFragment
import gd.app.musicplayer.core.ui.view.PlayerSheetInsetHost
import gd.app.musicplayer.core.ui.view.MusicRecyclerView
import gd.app.musicplayer.core.extension.dpToPx
import gd.app.musicplayer.core.extension.isTablet
import gd.app.musicplayer.core.extension.parcelable

internal const val ARG_MUSIC_SET = "music_set"
internal const val ARG_MUSIC = "music"
internal interface ListMoreMenuHost {
    fun showMoreMenu(anchor: View)
}

abstract class BaseListFragment : ViewBindingFragment<LayoutRecyclerviewBinding>(), ListMoreMenuHost {
    private var gridDecoration: RecyclerView.ItemDecoration? = null

    protected lateinit var musicSet: MusicSet
        private set

    private lateinit var musicRecyclerView: MusicRecyclerView

    override fun onCreateBinding(inflater: LayoutInflater): LayoutRecyclerviewBinding =
        LayoutRecyclerviewBinding.inflate(inflater)

    override fun onBindingCreated(binding: LayoutRecyclerviewBinding, savedInstanceState: Bundle?) {
        super.onBindingCreated(binding, savedInstanceState)

        musicRecyclerView = binding.recyclerview
        musicRecyclerView.setHasFixedSize(true)
        musicRecyclerView.isNestedScrollingEnabled = true
        musicSet = requireNotNull(requireArguments().parcelable(ARG_MUSIC_SET))
    }

    override fun onResume() {
        super.onResume()
    }

    protected fun setupRecyclerView(adapter: RecyclerView.Adapter<*>) {
        musicRecyclerView.adapter = adapter
        setListLayoutManager()
    }

    protected fun setListLayoutManager() {
        clearGridDecoration()
        musicRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        musicRecyclerView.setPadding(0, 0, 0, 0)
    }

    protected fun setGridLayoutManager() {
        clearGridDecoration()
        val spanCount = resolveSpanCount() // tablet+orientation aware
        val spacing = requireContext().dpToPx(if (requireContext().isTablet()) 16f else 2f)

        musicRecyclerView.layoutManager = GridLayoutManager(requireContext(), spanCount)
        musicRecyclerView.setPadding(spacing, spacing, spacing, spacing)
        musicRecyclerView.clipToPadding = false

        gridDecoration = SpacingItemDecoration.all(spacing).also {
            musicRecyclerView.addItemDecoration(it)
        }
    }

    private fun clearGridDecoration() {
        gridDecoration?.let { musicRecyclerView.removeItemDecoration(it) }
        gridDecoration = null
    }

    private fun resolveSpanCount(): Int {
        val landscape =
            resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        return when {
            !requireContext().isTablet() && !landscape -> 2
            !requireContext().isTablet() && landscape -> 3
            requireContext().isTablet() && !landscape -> 3
            else -> 4
        }
    }
}
