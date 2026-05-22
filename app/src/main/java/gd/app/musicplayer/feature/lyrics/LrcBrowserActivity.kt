package gd.app.musicplayer.feature.lyrics

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.parcelable
import gd.app.musicplayer.core.common.extension.startActivityCompat
import gd.app.musicplayer.core.common.util.ToastUtil
import gd.app.musicplayer.core.designsystem.dialog.createMessageDialogConfig
import gd.app.musicplayer.core.designsystem.dialog.showMessageDialog
import gd.app.musicplayer.core.designsystem.theme.itemTextColor
import gd.app.musicplayer.core.designsystem.view.MusicRecyclerView
import gd.app.musicplayer.databinding.ActivityLrcBrowserBinding
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.ui.common.base.BaseActivity
import gd.app.musicplayer.ui.common.base.RecyclerEmptyStateController
import gd.app.musicplayer.ui.common.base.setupEdgeToEdgeToolbar
import gd.app.musicplayer.util.TrackLyricsStore
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class LrcBrowserActivity : BaseActivity() {

    private lateinit var binding: ActivityLrcBrowserBinding
    private lateinit var adapter: BrowserAdapter
    private lateinit var emptyStateController: RecyclerEmptyStateController
    private lateinit var layoutManager: LinearLayoutManager

    private var track: Music? = null
    private var rootDirectory: BrowserDirectory? = null
    private var currentDirectory: BrowserDirectory? = null
    private var loadJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        track = intent.parcelable(EXTRA_TRACK)
        if (track == null) {
            finish()
            return
        }

        if (track?.data.isNullOrBlank()) {
            ToastUtil.show(this, R.string.music_unsupported)
            finish()
            return
        }

        binding = ActivityLrcBrowserBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupList()
        openRoot()
    }

    override fun onResume() {
        super.onResume()
        applyThemeTo(binding.root)
        emptyStateController.applyTheme(themeEngine.currentTheme())
    }

    override fun onBackPressed() {
        val parent = currentDirectory?.parentDirectory
        if (parent != null) {
            loadDirectory(parent, restoreScroll = true)
            return
        }
        super.onBackPressed()
    }

    override fun onDestroy() {
        loadJob?.cancel()
        super.onDestroy()
    }

    private fun setupToolbar() {
        setupEdgeToEdgeToolbar(
            root = binding.root,
            statusBarView = binding.statusBarSpace,
            toolbar = binding.toolbar,
            bottomPaddingView = binding.root,
            titleRes = R.string.file_choose
        )
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupList() {
        layoutManager = LinearLayoutManager(this)
        adapter = BrowserAdapter(
            onItemClick = ::handleItemClick,
            onItemLongClick = ::handleItemLongClick
        )

        findViewById<MusicRecyclerView>(R.id.recyclerview).apply {
            layoutManager = this@LrcBrowserActivity.layoutManager
            adapter = this@LrcBrowserActivity.adapter
            setHasFixedSize(true)
        }

        emptyStateController = RecyclerEmptyStateController(
            recyclerView = findViewById(R.id.recyclerview),
            emptyViewStub = findViewById(R.id.layout_list_empty)
        ).apply {
            setEmptyMessage(getString(R.string.no_lrc_1))
            applyTheme(themeEngine.currentTheme())
        }
    }

    private fun openRoot() {
        val root = BrowserDirectory(
            parentDirectory = null,
            title = getString(R.string.music_directory),
            path = ROOT_PATH,
            iconRes = R.drawable.main_directory_simple
        ).apply {
            isLoaded = true
        }
        root.children = buildStorageRootItems(root)
        rootDirectory = root
        loadDirectory(root, restoreScroll = false)
    }

    private fun buildStorageRootItems(root: BrowserDirectory): List<BrowserItem> {
        val rootPaths = linkedSetOf<File>()

        Environment.getExternalStorageDirectory()
            ?.takeIf { it.isDirectory && it.canRead() }
            ?.let(rootPaths::add)

        getExternalFilesDirs(null)
            .orEmpty()
            .mapNotNull(::externalStorageRootOf)
            .filter { it.isDirectory && it.canRead() }
            .forEach(rootPaths::add)

        return rootPaths.mapIndexed { index, file ->
            BrowserDirectory(
                parentDirectory = root,
                title = if (index == 0) {
                    getString(R.string.internal_storage)
                } else {
                    val base = getString(R.string.sd_card)
                    if (index == 1) base else "$base $index"
                },
                path = file.absolutePath,
                iconRes = if (index == 0) {
                    R.drawable.vector_internal_storage
                } else {
                    R.drawable.vector_sd_card
                }
            )
        }
    }

    private fun externalStorageRootOf(file: File?): File? {
        if (file == null) return null
        val marker = "${File.separator}Android${File.separator}"
        val path = file.absolutePath
        val androidIndex = path.indexOf(marker)
        if (androidIndex > 0) {
            return File(path.substring(0, androidIndex))
        }

        var current: File? = file
        repeat(4) {
            current = current?.parentFile
        }
        return current
    }

    private fun loadDirectory(
        directory: BrowserDirectory,
        restoreScroll: Boolean
    ) {
        loadJob?.cancel()
        loadJob = lifecycleScope.launch {
            val items = if (directory.isLoaded) {
                directory.children
            } else {
                withContext(Dispatchers.IO) {
                    scanDirectory(directory)
                }.also {
                    directory.children = it
                    directory.isLoaded = true
                }
            }

            currentDirectory = directory
            updateToolbar(directory)
            adapter.submit(items)
            emptyStateController.setVisible(items.isEmpty())

            if (restoreScroll) {
                layoutManager.scrollToPositionWithOffset(
                    directory.savedScrollPosition,
                    directory.savedScrollTop
                )
            } else {
                layoutManager.scrollToPositionWithOffset(0, 0)
            }
        }
    }

    private fun scanDirectory(directory: BrowserDirectory): List<BrowserItem> {
        return File(directory.path)
            .listFiles()
            .orEmpty()
            .asSequence()
            .filterNot { it.isHidden }
            .mapNotNull { file ->
                when {
                    file.isDirectory && file.canRead() -> BrowserDirectory(
                        parentDirectory = directory,
                        title = file.name,
                        path = file.absolutePath,
                        iconRes = R.drawable.main_folder_simple_t
                    )

                    file.isFile && file.name.endsWith(LRC_EXTENSION, ignoreCase = true) ->
                        BrowserLyricFile(
                            parentDirectory = directory,
                            title = file.name,
                            path = file.absolutePath
                        )

                    else -> null
                }
            }
            .sortedWith(
                compareBy<BrowserItem> { if (it is BrowserDirectory) 0 else 1 }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
            )
            .toList()
    }

    private fun updateToolbar(directory: BrowserDirectory) {
        if (directory.parentDirectory == null) {
            binding.toolbar.setTitle(R.string.scan_specified_folder)
            binding.toolbar.subtitle = null
            return
        }

        binding.toolbar.title = directory.title
        binding.toolbar.subtitle = directory.path
        binding.toolbar.setSubtitleTextColor(themeEngine.currentTheme().itemTextColor)
    }

    private fun saveScrollPosition(directory: BrowserDirectory?) {
        directory ?: return
        val position = layoutManager.findFirstVisibleItemPosition()
        if (position == RecyclerView.NO_POSITION) return

        directory.savedScrollPosition = position
        directory.savedScrollTop = layoutManager.findViewByPosition(position)?.top ?: 0
    }

    private fun handleItemClick(item: BrowserItem) {
        when (item) {
            is BrowserDirectory -> {
                saveScrollPosition(currentDirectory)
                loadDirectory(item, restoreScroll = false)
            }

            is BrowserLyricFile -> {
                val currentTrack = track ?: return
                TrackLyricsStore.from(this).setTrackLyricPath(currentTrack.id, item.path)
                setResult(Activity.RESULT_OK)
                finish()
            }
        }
    }

    private fun handleItemLongClick(item: BrowserItem): Boolean {
        if (item !is BrowserLyricFile) return false

        showMessageDialog(
            createMessageDialogConfig(
                title = item.title,
                message = getString(R.string.delete_file_tip, item.title),
                positiveText = getString(R.string.delete),
                negativeText = getString(R.string.cancel),
                positiveClickListener = { dialog, _ ->
                    dialog.dismiss()
                    runCatching { File(item.path).delete() }
                    item.parentDirectory.isLoaded = false
                    loadDirectory(item.parentDirectory, restoreScroll = true)
                },
                negativeClickListener = { dialog, _ ->
                    dialog.dismiss()
                }
            )
        )
        return true
    }

    private sealed class BrowserItem(
        open val parentDirectory: BrowserDirectory?,
        open val title: String,
        open val path: String
    )

    private class BrowserDirectory(
        override val parentDirectory: BrowserDirectory?,
        override val title: String,
        override val path: String,
        val iconRes: Int,
        var children: List<BrowserItem> = emptyList()
    ) : BrowserItem(parentDirectory, title, path) {
        var isLoaded: Boolean = false
        var savedScrollPosition: Int = 0
        var savedScrollTop: Int = 0
    }

    private class BrowserLyricFile(
        override val parentDirectory: BrowserDirectory,
        override val title: String,
        override val path: String
    ) : BrowserItem(parentDirectory, title, path)

    private inner class BrowserAdapter(
        private val onItemClick: (BrowserItem) -> Unit,
        private val onItemLongClick: (BrowserItem) -> Boolean
    ) : RecyclerView.Adapter<BrowserAdapter.ViewHolder>() {

        private val items = mutableListOf<BrowserItem>()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.activity_lrc_browser_list_item, parent, false)
            )
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position])
        }

        fun submit(newItems: List<BrowserItem>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        private inner class ViewHolder(
            itemView: View
        ) : RecyclerView.ViewHolder(itemView) {

            private val image: ImageView = itemView.findViewById(R.id.lrc_browser_item_image)
            private val title: TextView = itemView.findViewById(R.id.lrc_browser_item_title)

            fun bind(item: BrowserItem) {
                image.setImageDrawable(
                    AppCompatResources.getDrawable(
                        itemView.context,
                        when (item) {
                            is BrowserDirectory -> item.iconRes
                            is BrowserLyricFile -> R.drawable.vector_default_lrc
                        }
                    )
                )
                title.text = item.title
                title.setTextColor(themeEngine.currentTheme().itemTextColor)
                itemView.setOnClickListener { onItemClick(item) }
                itemView.setOnLongClickListener { onItemLongClick(item) }
            }
        }
    }

    companion object {
        private const val EXTRA_TRACK = "KEY_MUSIC"
        private const val ROOT_PATH = "/"
        private const val LRC_EXTENSION = ".lrc"

        fun intent(context: Context, track: Music): Intent =
            Intent(context, LrcBrowserActivity::class.java).putExtra(EXTRA_TRACK, track)

        fun start(context: Context, track: Music) {
            context.startActivityCompat(intent(context, track))
        }
    }
}
