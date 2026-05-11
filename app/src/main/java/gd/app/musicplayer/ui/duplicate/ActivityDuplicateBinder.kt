package gd.app.musicplayer.ui.duplicate

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import gd.app.musicplayer.R
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.core.common.extension.loadMusicArtwork
import gd.app.musicplayer.databinding.ActivityDuplicatedFinderChildItemBinding
import gd.app.musicplayer.databinding.ActivityDuplicatedFinderGroupItemBinding
import java.util.Locale

class ActivityDuplicateBinder(
    private val onGroupClick: (String) -> Unit,
    private val onTrackClick: (Long) -> Unit,
    private val applyTheme: (View) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var rows: List<Row> = emptyList()
    private var selectedIds: Set<Long> = emptySet()

    init {
        setHasStableIds(true)
    }

    fun submit(groups: List<DuplicateGroup>, selectedIds: Set<Long>) {
        val newRows = buildRows(groups)
        val oldRows = rows
        val oldSelectedIds = this.selectedIds

        rows = newRows
        this.selectedIds = selectedIds.toSet()

        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = oldRows.size

            override fun getNewListSize(): Int = newRows.size

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return oldRows[oldItemPosition].stableId == newRows[newItemPosition].stableId
            }

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return oldRows[oldItemPosition] == newRows[newItemPosition]
            }
        })
        diff.dispatchUpdatesTo(this)

        if (oldRows == newRows && oldSelectedIds != this.selectedIds) {
            notifyItemRangeChanged(0, itemCount, PAYLOAD_SELECTION)
        }
    }

    override fun getItemId(position: Int): Long = rows[position].stableId

    override fun getItemCount(): Int = rows.size

    override fun getItemViewType(position: Int): Int {
        return when (rows[position]) {
            is Row.GroupRow -> VIEW_TYPE_GROUP
            is Row.ChildRow -> VIEW_TYPE_CHILD
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_GROUP -> {
                val binding = ActivityDuplicatedFinderGroupItemBinding.inflate(
                    inflater,
                    parent,
                    false
                )
                applyTheme(binding.root)
                GroupViewHolder(binding, onGroupClick)
            }

            VIEW_TYPE_CHILD -> {
                val binding = ActivityDuplicatedFinderChildItemBinding.inflate(
                    inflater,
                    parent,
                    false
                )
                applyTheme(binding.root)
                ChildViewHolder(binding, onTrackClick)
            }

            else -> error("Unsupported duplicate row type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is Row.GroupRow -> (holder as GroupViewHolder).bind(row.group)
            is Row.ChildRow -> (holder as ChildViewHolder).bind(
                music = row.music,
                index = row.indexInGroup,
                selected = row.music.id in selectedIds
            )
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.contains(PAYLOAD_SELECTION) && holder is ChildViewHolder) {
            val row = rows[position] as? Row.ChildRow ?: return
            holder.updateSelection(row.music.id in selectedIds)
            return
        }
        super.onBindViewHolder(holder, position, payloads)
    }

    private fun buildRows(groups: List<DuplicateGroup>): List<Row> {
        return buildList {
            groups.forEach { group ->
                add(Row.GroupRow(group))
                if (group.expanded) {
                    group.tracks.forEachIndexed { index, music ->
                        add(
                            Row.ChildRow(
                                groupKey = group.key,
                                music = music,
                                indexInGroup = index
                            )
                        )
                    }
                }
            }
        }
    }

    private sealed interface Row {
        val stableId: Long

        data class GroupRow(val group: DuplicateGroup) : Row {
            override val stableId: Long = ("group:${group.key}").hashCode().toLong()
        }

        data class ChildRow(
            val groupKey: String,
            val music: Music,
            val indexInGroup: Int
        ) : Row {
            override val stableId: Long = ("child:$groupKey:${music.id}").hashCode().toLong()
        }
    }

    private class GroupViewHolder(
        private val binding: ActivityDuplicatedFinderGroupItemBinding,
        private val onGroupClick: (String) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        private var groupKey: String = ""

        fun bind(group: DuplicateGroup) {
            groupKey = group.key
            val firstTrack = group.tracks.first()
            binding.musicItemTitle.text = firstTrack.title
            binding.musicItemArtist.text = buildHeaderSubtitle(firstTrack)
            binding.musicItemAlbum.setImageResource(R.drawable.default_album_identify)
            firstTrack.loadMusicArtwork(binding.musicItemAlbum)
            binding.root.setOnClickListener {
                onGroupClick(groupKey)
            }
        }

        private fun buildHeaderSubtitle(music: Music): String {
            val durationText = formatDuration(music.duration)
            val artist = music.artist.takeIf { it.isNotBlank() }.orEmpty()
            return if (artist.isBlank()) durationText else "$durationText $artist"
        }
    }

    private class ChildViewHolder(
        private val binding: ActivityDuplicatedFinderChildItemBinding,
        private val onTrackClick: (Long) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        private var trackId: Long = -1L

        fun bind(music: Music, index: Int, selected: Boolean) {
            trackId = music.id
            binding.musicItemTitle.text = music.title
            binding.musicItemArtist.text = music.artist
            binding.musicItemNumber.text = (index + 1).toString()
            updateSelection(selected)
            binding.root.setOnClickListener {
                onTrackClick(trackId)
            }
        }

        fun updateSelection(selected: Boolean) {
            binding.musicItemMenu.isSelected = selected
        }
    }

    private companion object {
        const val VIEW_TYPE_GROUP = 0
        const val VIEW_TYPE_CHILD = 1
        const val PAYLOAD_SELECTION = "selection"

        fun formatDuration(durationMs: Int): String {
            val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            return if (hours > 0) {
                String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
            }
        }
    }
}
