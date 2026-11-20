package gd.app.musicplayer.feature.library.directory

import android.content.Context
import android.os.Environment
import gd.app.musicplayer.R
import gd.app.musicplayer.domain.model.Music
import java.io.File

/**
 * Port of the original directory-tree model (`w5.a` / `w5.c` / `w5.d`) used by
 * `ActivityMusicDirectory` for `MusicSet` id `-16`.
 */
sealed class DirectoryEntry {
    abstract val path: String
    abstract val name: String
    abstract val parent: DirectoryNode?
}

class DirectoryNode(
    override val path: String,
    override val name: String,
    override val parent: DirectoryNode?,
    /** Storage-root icon (`w5.c.s`); `0` falls back to the generic folder art. */
    val iconRes: Int = 0,
    /** `w5.c.q`: volume list node whose children are fixed and must not be re-scanned. */
    val hasFixedChildren: Boolean = false
) : DirectoryEntry() {

    var children: List<DirectoryEntry> = emptyList()
        private set

    /** `w5.c.p`: children have been scanned at least once, so counts can be shown. */
    var isScanned: Boolean = false
        private set

    var savedPosition: Int = 0
    var savedOffset: Int = 0

    val folderCount: Int
        get() = children.count { it is DirectoryNode }

    val trackCount: Int
        get() = children.count { it is DirectoryTrack }

    fun setChildren(items: List<DirectoryEntry>) {
        children = items
        isScanned = true
    }

    /** `w5.c.k`: every track in this folder, with [clicked] appended when it is not listed. */
    fun tracks(clicked: Music? = null): List<Music> {
        val tracks = children.filterIsInstance<DirectoryTrack>().map { it.music }
        if (clicked == null || tracks.contains(clicked)) return tracks
        return tracks + clicked
    }
}

class DirectoryTrack(
    override val path: String,
    override val name: String,
    override val parent: DirectoryNode,
    val music: Music
) : DirectoryEntry()

/** `w5.b.f16229a`: folders before files, then case-insensitive name. */
private val entryComparator = Comparator<DirectoryEntry> { left, right ->
    val leftIsFolder = left is DirectoryNode
    val rightIsFolder = right is DirectoryNode

    when {
        leftIsFolder && !rightIsFolder -> -1
        !leftIsFolder && rightIsFolder -> 1
        else -> left.name.compareTo(right.name, ignoreCase = true)
    }
}

/** `w5.b.a`: synthetic root holding one child per mounted storage volume. */
fun buildDirectoryRoot(context: Context): DirectoryNode {
    val root = DirectoryNode(
        path = "/",
        name = context.getString(R.string.music_directory),
        parent = null,
        hasFixedChildren = true
    )

    val volumes = storageRoots(context).mapIndexed { index, path ->
        DirectoryNode(
            path = path,
            name = when (index) {
                0 -> context.getString(R.string.internal_storage)
                1 -> context.getString(R.string.sd_card)
                else -> context.getString(R.string.sd_card) + (index - 1)
            },
            parent = root,
            iconRes = if (index == 0) {
                R.drawable.vector_internal_storage
            } else {
                R.drawable.vector_sd_card
            }
        )
    }

    root.setChildren(volumes)

    return root
}

/** `w5.b.c`: list visible sub-directories plus the scanned tracks that live in [node]. */
fun loadDirectoryChildren(
    node: DirectoryNode,
    tracksByFolder: Map<String, List<Music>>
): List<DirectoryEntry> {
    if (node.hasFixedChildren) return node.children

    val directory = File(node.path)
    if (directory.isHidden || !directory.isDirectory) {
        node.setChildren(emptyList())
        return emptyList()
    }

    val entries = mutableListOf<DirectoryEntry>()

    directory.listFiles()
        ?.filter { file -> !file.isHidden && file.isDirectory }
        ?.forEach { file ->
            entries += DirectoryNode(
                path = file.absolutePath,
                name = file.name,
                parent = node
            )
        }

    tracksByFolder[node.path]?.forEach { music ->
        val trackPath = music.data.orEmpty()
        entries += DirectoryTrack(
            path = trackPath,
            name = File(trackPath).name.ifBlank { music.displayTitle },
            parent = node,
            music = music
        )
    }

    entries.sortWith(entryComparator)
    node.setChildren(entries)

    return entries
}

/**
 * `ActivityMusicDirectory.m1`: group every scanned track by its parent directory, skipping rows
 * whose file no longer exists on disk.
 */
fun groupTracksByFolder(tracks: List<Music>): Map<String, List<Music>> {
    val grouped = linkedMapOf<String, MutableList<Music>>()

    tracks.forEach { music ->
        val filePath = music.data
        if (filePath.isNullOrBlank()) return@forEach

        val file = File(filePath)
        val parentPath = music.folderPath?.takeIf(String::isNotBlank)
            ?: file.parent
            ?: return@forEach

        grouped.getOrPut(parentPath) { mutableListOf() }.add(music)
    }

    return grouped
}

private fun storageRoots(context: Context): List<String> {
    val roots = linkedSetOf<String>()

    @Suppress("DEPRECATION")
    Environment.getExternalStorageDirectory()?.absolutePath
        ?.trimEnd('/')
        ?.takeIf(String::isNotBlank)
        ?.let(roots::add)

    context.getExternalFilesDirs(null)
        .orEmpty()
        .mapNotNull { file -> file?.absolutePath?.substringBefore("/Android/") }
        .map { path -> path.trimEnd('/') }
        .filter(String::isNotBlank)
        .forEach(roots::add)

    return roots.toList()
}
