package gd.app.musicplayer.ui.theme

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListUpdateCallback
import androidx.viewpager2.adapter.FragmentStateAdapter
import gd.app.musicplayer.R
import gd.app.musicplayer.domain.model.ThemeGroup

class ThemePagerAdapter(
    private val activity: FragmentActivity,
    private var groups: List<ThemeGroup> = emptyList()
) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = groups.size + 1

    override fun createFragment(position: Int): Fragment {
        return ThemePagerFragment.newInstance(position)
    }

    override fun getItemId(position: Int): Long {
        return when (position) {
            0 -> ALL_TAB_ID
            else -> groups[position - 1].type.hashCode().toLong()
        }
    }

    override fun containsItem(itemId: Long): Boolean {
        return itemId == ALL_TAB_ID || groups.any { it.type.hashCode().toLong() == itemId }
    }

    fun titleFor(position: Int): String {
        return if (position == 0) {
            activity.getString(R.string.themes_classify_all)
        } else {
            groups.getOrNull(position - 1)?.type.orEmpty()
        }
    }

    fun submitList(newGroups: List<ThemeGroup>) {
        if (groups == newGroups) return

        val oldGroups = groups
        groups = newGroups

        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = oldGroups.size
            override fun getNewListSize(): Int = newGroups.size

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return oldGroups[oldItemPosition].type == newGroups[newItemPosition].type
            }

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return oldGroups[oldItemPosition] == newGroups[newItemPosition]
            }
        })

        diff.dispatchUpdatesTo(object : ListUpdateCallback {
            override fun onInserted(position: Int, count: Int) {
                notifyItemRangeInserted(position + 1, count)
            }

            override fun onRemoved(position: Int, count: Int) {
                notifyItemRangeRemoved(position + 1, count)
            }

            override fun onMoved(fromPosition: Int, toPosition: Int) {
                notifyItemMoved(fromPosition + 1, toPosition + 1)
            }

            override fun onChanged(position: Int, count: Int, payload: Any?) {
                notifyItemRangeChanged(position + 1, count, payload)
            }
        })
    }

    private companion object {
        private const val ALL_TAB_ID = Long.MIN_VALUE
    }
}
