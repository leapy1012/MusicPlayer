package gd.app.musicplayer.feature.library

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import gd.app.musicplayer.core.common.extension.dpToPx
import gd.app.musicplayer.core.common.extension.isTablet
import gd.app.musicplayer.core.common.extension.parcelable
import gd.app.musicplayer.core.designsystem.view.MusicRecyclerView
import gd.app.musicplayer.domain.model.MusicSet
import gd.app.musicplayer.databinding.LayoutRecyclerviewBinding
import gd.app.musicplayer.ui.common.base.SpacingItemDecoration
import gd.app.musicplayer.ui.common.base.ViewBindingFragment

internal const val ARG_MUSIC_SET = "music_set"
internal const val ARG_MUSIC = "music"

internal interface ListMoreMenuHost {
    fun showMoreMenu(anchor: View)
}

abstract class BaseListFragment :
    ViewBindingFragment<LayoutRecyclerviewBinding>(),
    ListMoreMenuHost {

    private var gridDecoration: RecyclerView.ItemDecoration? = null

    protected lateinit var musicSet: MusicSet
        private set

    private lateinit var musicRecyclerView: MusicRecyclerView

    override fun onCreateBinding(
        inflater: LayoutInflater
    ): LayoutRecyclerviewBinding {
        return LayoutRecyclerviewBinding.inflate(inflater)
    }

    override fun onBindingCreated(
        binding: LayoutRecyclerviewBinding,
        savedInstanceState: Bundle?
    ) {
        super.onBindingCreated(binding, savedInstanceState)

        musicRecyclerView = binding.recyclerview

        musicRecyclerView.setHasFixedSize(true)
        musicRecyclerView.isNestedScrollingEnabled = true
        musicRecyclerView.itemAnimator = null

        musicSet = requireNotNull(
            requireArguments().parcelable(ARG_MUSIC_SET)
        )
    }

    protected fun updateMusicSet(newSet: MusicSet) {
        musicSet = newSet
    }

    protected fun setupRecyclerView(
        adapter: RecyclerView.Adapter<*>
    ) {
        if (musicRecyclerView.adapter !== adapter) {
            musicRecyclerView.adapter = adapter
        }

        if (musicRecyclerView.layoutManager == null) {
            setListLayoutManager()
        }
    }

    protected fun setListLayoutManager() {
        if (musicRecyclerView.layoutManager is LinearLayoutManager &&
            musicRecyclerView.layoutManager !is GridLayoutManager
        ) {
            clearGridDecoration()
            musicRecyclerView.setPadding(0, 0, 0, 0)
            musicRecyclerView.clipToPadding = true
            return
        }

        clearGridDecoration()

        musicRecyclerView.layoutManager = LinearLayoutManager(
            requireContext()
        )

        musicRecyclerView.setPadding(0, 0, 0, 0)
        musicRecyclerView.clipToPadding = true
    }

    protected fun setGridLayoutManager() {
        val currentLayoutManager = musicRecyclerView.layoutManager
        val spanCount = resolveSpanCount()

        if (
            currentLayoutManager is GridLayoutManager &&
            currentLayoutManager.spanCount == spanCount
        ) {
            return
        }

        clearGridDecoration()

        val spacing = requireContext().dpToPx(
            if (requireContext().isTablet()) {
                TABLET_GRID_SPACING_DP
            } else {
                PHONE_GRID_SPACING_DP
            }
        )

        musicRecyclerView.layoutManager = GridLayoutManager(
            requireContext(),
            spanCount
        )

        musicRecyclerView.setPadding(
            spacing,
            spacing,
            spacing,
            spacing
        )

        musicRecyclerView.clipToPadding = false

        gridDecoration = SpacingItemDecoration
            .all(spacing)
            .also { decoration ->
                musicRecyclerView.addItemDecoration(decoration)
            }
    }

    private fun clearGridDecoration() {
        gridDecoration?.let { decoration ->
            musicRecyclerView.removeItemDecoration(decoration)
        }

        gridDecoration = null
    }

    private fun resolveSpanCount(): Int {
        val isLandscape =
            resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        return when {
            !requireContext().isTablet() && !isLandscape -> 2
            !requireContext().isTablet() && isLandscape -> 3
            requireContext().isTablet() && !isLandscape -> 3
            else -> 4
        }
    }

    private companion object {
        private const val PHONE_GRID_SPACING_DP = 2f
        private const val TABLET_GRID_SPACING_DP = 16f
    }
}
