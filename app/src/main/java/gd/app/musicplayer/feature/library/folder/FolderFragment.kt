package gd.app.musicplayer.feature.library.folder

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import androidx.fragment.app.commit
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.domain.model.MusicSet
import gd.app.musicplayer.databinding.FragmentFolderBinding
import gd.app.musicplayer.ui.common.base.ViewBindingFragment
import gd.app.musicplayer.feature.library.ListMoreMenuHost
import gd.app.musicplayer.feature.library.musicset.MusicSetListFragment
import gd.app.musicplayer.feature.search.SearchActivity
import gd.app.musicplayer.core.common.extension.applySystemBarInsets
import gd.app.musicplayer.core.common.extension.navigateBack

@AndroidEntryPoint
class FolderFragment : ViewBindingFragment<FragmentFolderBinding>() {

    override fun onCreateBinding(inflater: LayoutInflater) =
        FragmentFolderBinding.inflate(inflater)

    override fun onBindingCreated(binding: FragmentFolderBinding, savedInstanceState: Bundle?) =
        with(binding) {
            super.onBindingCreated(binding, savedInstanceState)

            root.applySystemBarInsets(statusBarSpace, root)
            toolbar.navigateBack(this@FolderFragment)
            toolbar.setOnMenuItemClickListener(::onMenuClick)

            if (savedInstanceState == null && childFragmentManager.findFragmentById(
                    fragmentContainer.id
                ) == null
            ) {
                childFragmentManager.commit {
                    setReorderingAllowed(true)
                    replace(
                        binding.fragmentContainer.id,
                        MusicSetListFragment.newInstance(MusicSet.Folders)
                    )
                }
            }
        }

    private fun onMenuClick(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_search -> {
                SearchActivity.start(requireContext())
                true
            }

            R.id.menu_more -> requireBinding().let { binding ->
                (childFragmentManager.findFragmentById(binding.fragmentContainer.id) as? ListMoreMenuHost)
                    ?.let {
                        it.showMoreMenu(binding.toolbar.findViewById(R.id.menu_more) ?: binding.toolbar)
                        true
                    } ?: false
            }

            else -> false
        }
    }

    companion object {
        fun newInstance(): FolderFragment = FolderFragment()
    }
}

