package gd.app.musicplayer.ui.lyrics

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.text.Editable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.parcelable
import gd.app.musicplayer.core.common.extension.startActivityCompat
import gd.app.musicplayer.core.common.util.ToastUtil
import gd.app.musicplayer.core.designsystem.theme.accentColor
import gd.app.musicplayer.core.designsystem.theme.itemTextColor
import gd.app.musicplayer.core.designsystem.view.CustomSpinner
import gd.app.musicplayer.core.designsystem.view.MusicRecyclerView
import gd.app.musicplayer.core.designsystem.view.RecyclerIndexBar
import gd.app.musicplayer.databinding.ActivityLyricListBinding
import gd.app.musicplayer.databinding.ActivityLyricListItemBinding
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.ui.common.base.BaseActivity
import gd.app.musicplayer.ui.common.base.RecyclerEmptyStateController
import gd.app.musicplayer.ui.common.base.setupEdgeToEdgeToolbar
import gd.app.musicplayer.util.TrackLyricsStore
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class LyricListActivity :
    BaseActivity(),
    TextWatcher,
    AdapterView.OnItemClickListener,
    Toolbar.OnMenuItemClickListener {

    private lateinit var binding: ActivityLyricListBinding
    private lateinit var adapter: LyricFileAdapter
    private lateinit var emptyStateController: RecyclerEmptyStateController
    private lateinit var layoutManager: LinearLayoutManager

    private var track: Music? = null
    private var searchText = ""

    private val lrcBrowserLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                setResult(Activity.RESULT_OK)
                finish()
            } else {
                loadLyrics()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        track = intent.parcelable(EXTRA_TRACK)
        if (track == null) {
            finish()
            return
        }

        binding = ActivityLyricListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupList()
        setupSearch()
        setupSpinner()

        loadLyrics()
    }

    override fun onResume() {
        super.onResume()
        applyThemeTo(binding.root)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) {
            hideKeyboard()
        }
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_folder -> {
                track?.let { currentTrack ->
                    lrcBrowserLauncher.launch(LrcBrowserActivity.intent(this, currentTrack))
                }
                true
            }

            else -> false
        }
    }

    override fun onItemClick(
        parent: AdapterView<*>?,
        view: View?,
        position: Int,
        id: Long
    ) {
        loadLyrics()
    }

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

    override fun afterTextChanged(editable: Editable?) {
        searchText = editable?.toString().orEmpty().lowercase()
        binding.searchCloseBtn.isVisible = searchText.isNotEmpty()
        adapter.filter(searchText)
    }

    private fun setupToolbar() {
        setupEdgeToEdgeToolbar(
            root = binding.root,
            statusBarView = binding.statusBarSpace,
            toolbar = binding.toolbar,
            bottomPaddingView = binding.root,
            titleRes = R.string.lyrics_selection
        )

        binding.toolbar.inflateMenu(R.menu.menu_activity_lyric_list)
        binding.toolbar.setOnMenuItemClickListener(this)
        binding.toolbar.menu.findItem(R.id.menu_folder)?.icon?.let { icon ->
            DrawableCompat.setTintList(
                icon.mutate(),
                ColorStateList.valueOf(themeEngine.currentTheme().accentColor)
            )
        }
    }

    private fun setupList() {
        layoutManager = LinearLayoutManager(this)
        adapter = LyricFileAdapter(
            onItemClick = ::selectLyric,
            onItemLongClick = ::deleteLyricFile
        )

        binding.root.findViewById<MusicRecyclerView>(R.id.recyclerview).apply {
            layoutManager = this@LyricListActivity.layoutManager
            adapter = this@LyricListActivity.adapter
            setHasFixedSize(true)
        }

        emptyStateController = RecyclerEmptyStateController(
            recyclerView = binding.root.findViewById(R.id.recyclerview),
            emptyViewStub = binding.root.findViewById(R.id.layout_list_empty)
        ).apply {
            setEmptyMessage(getString(R.string.no_lrc_1))
            applyTheme(themeEngine.currentTheme())
        }

        binding.root.findViewById<RecyclerIndexBar>(R.id.recyclerview_index).apply {
            onLabelSelected = { label ->
                adapter.firstPositionForLabel(label)?.let { position ->
                    layoutManager.scrollToPositionWithOffset(position, 0)
                }
            }
        }

        adapter.onVisibleItemsChanged = { files ->
            emptyStateController.setVisible(files.isEmpty())
            binding.root.findViewById<RecyclerIndexBar>(R.id.recyclerview_index)
                .submitLabels(files.mapNotNull(::indexLabelFor))
        }
    }

    private fun setupSearch() {
        binding.searchEditText.addTextChangedListener(this)
        binding.searchCloseBtn.setOnClickListener {
            binding.searchEditText.setText("")
        }
    }

    private fun setupSpinner() {
        binding.mainInfoSpinner.setEntriesResourceId(R.array.search_lyric_array)
        binding.mainInfoSpinner.setSelection(0)
        binding.mainInfoSpinner.setOnItemClickListener(this)
    }

    private fun loadLyrics() {
        val currentTrack = track ?: return
        emptyStateController.setLoadingEnabled(true)
        lifecycleScope.launch {
            val files = withContext(Dispatchers.IO) {
                if (binding.mainInfoSpinner.getSelection() == 0) {
                    findRelatedLyrics(currentTrack)
                } else {
                    findAllLyrics()
                }
            }
            emptyStateController.setLoadingEnabled(false)
            if (isFinishing || isDestroyed) return@launch
            adapter.submit(files)
            adapter.filter(searchText)
        }
    }

    private fun findRelatedLyrics(track: Music): List<LyricFile> {
        val title = track.title.lowercase()
        val baseName = track.data
            ?.let(::File)
            ?.nameWithoutExtension
            ?.lowercase()
            .orEmpty()

        val candidates = linkedSetOf<LyricFile>()
        TrackLyricsStore.from(this)
            .getTrackLyricPath(track.id)
            ?.let(::File)
            ?.takeIf { it.isFile }
            ?.let { candidates += LyricFile(it) }

        val scanDirs = buildList {
            add(File(filesDir, "lyrics"))
            track.data?.let { data ->
                File(data).parentFile?.let(::add)
            }
        }

        scanDirs.forEach { dir ->
            dir.walkLrcFiles(maxDepth = if (dir == File(filesDir, "lyrics")) Int.MAX_VALUE else 1)
                .filter { file ->
                    val name = file.name.lowercase()
                    (title.isNotBlank() && name.contains(title)) ||
                        (baseName.isNotBlank() && name.contains(baseName))
                }
                .forEach { candidates += LyricFile(it) }
        }

        return candidates.sortedWith(currentLyricFirstComparator(track.id))
    }

    private fun findAllLyrics(): List<LyricFile> {
        val files = linkedSetOf<LyricFile>()

        queryMediaStoreLyrics().forEach { files += it }
        File(filesDir, "lyrics")
            .walkLrcFiles(maxDepth = Int.MAX_VALUE)
            .forEach { files += LyricFile(it) }

        return files.sortedBy { it.title.lowercase() }
    }

    private fun queryMediaStoreLyrics(): List<LyricFile> {
        val result = mutableListOf<LyricFile>()
        val uri = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(MediaStore.Files.FileColumns.DATA)
        val selection = "${MediaStore.Files.FileColumns.SIZE}>0 AND ${MediaStore.Files.FileColumns.DATA} LIKE ?"
        val selectionArgs = arrayOf("%.lrc")
        val sortOrder = "${MediaStore.Files.FileColumns.TITLE} ASC"

        runCatching {
            contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
                val dataIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
                while (cursor.moveToNext()) {
                    val file = File(cursor.getString(dataIndex))
                    if (file.isFile) {
                        result += LyricFile(file)
                    }
                }
            }
        }

        return result
    }

    private fun selectLyric(file: LyricFile) {
        val currentTrack = track ?: return
        TrackLyricsStore.from(this).setTrackLyricPath(currentTrack.id, file.path)
        setResult(Activity.RESULT_OK)
        finish()
    }

    private fun selectExternalLyric(uri: Uri) {
        val currentTrack = track ?: return
        lifecycleScope.launch {
            val importedPath = withContext(Dispatchers.IO) {
                importLyricFile(currentTrack.id, uri)
            }
            if (importedPath == null) {
                ToastUtil.show(this@LyricListActivity, R.string.permission_open_failed)
                return@launch
            }
            selectLyric(LyricFile(File(importedPath)))
        }
    }

    private fun importLyricFile(trackId: Long, uri: Uri): String? {
        val extension = resolveExtension(uri)
        val target = File(filesDir, "lyrics/imported/track_${trackId}.$extension")
        return runCatching {
            target.parentFile?.mkdirs()
            contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: error("Unable to open source")
            target.absolutePath
        }.getOrNull()
    }

    private fun resolveExtension(uri: Uri): String {
        val name = runCatching {
            contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()

        return when (val extension = (name ?: uri.lastPathSegment.orEmpty())
            .substringAfterLast('.', "")
            .lowercase()) {
            "lrc", "txt" -> extension
            else -> "lrc"
        }
    }

    private fun deleteLyricFile(file: LyricFile) {
        val deleted = runCatching {
            val localFile = File(file.path)
            localFile.path.startsWith(filesDir.path) && localFile.delete()
        }.getOrDefault(false)

        if (deleted) {
            loadLyrics()
        }
    }

    private fun currentLyricFirstComparator(trackId: Long): Comparator<LyricFile> {
        val currentPath = TrackLyricsStore.from(this).getTrackLyricPath(trackId)
        return compareByDescending<LyricFile> { it.path == currentPath }
            .thenBy { it.title.lowercase() }
    }

    private fun File.walkLrcFiles(maxDepth: Int): Sequence<File> {
        if (!exists()) return emptySequence()
        return walkTopDown()
            .onEnter { directory ->
                val relativeDepth = directory.toRelativeString(this).let { relative ->
                    if (relative == ".") 0 else relative.count { it == File.separatorChar } + 1
                }
                relativeDepth < maxDepth
            }
            .filter { it.isFile && it.extension.equals("lrc", ignoreCase = true) }
    }

    private fun indexLabelFor(file: LyricFile): String? {
        val first = file.title.firstOrNull { it.isLetterOrDigit() } ?: return null
        return first.uppercaseChar().toString()
    }

    private fun hideKeyboard() {
        val imm = ContextCompat.getSystemService(this, InputMethodManager::class.java)
        imm?.hideSoftInputFromWindow(binding.searchEditText.windowToken, 0)
    }

    private inner class LyricFileAdapter(
        private val onItemClick: (LyricFile) -> Unit,
        private val onItemLongClick: (LyricFile) -> Unit
    ) : RecyclerView.Adapter<LyricFileAdapter.ViewHolder>() {

        private val allItems = mutableListOf<LyricFile>()
        private val visibleItems = mutableListOf<LyricFile>()

        var onVisibleItemsChanged: ((List<LyricFile>) -> Unit)? = null

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ActivityLyricListItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return ViewHolder(binding)
        }

        override fun getItemCount(): Int = visibleItems.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(visibleItems[position])
        }

        fun submit(items: List<LyricFile>) {
            allItems.clear()
            allItems.addAll(items)
        }

        fun filter(query: String) {
            visibleItems.clear()
            visibleItems.addAll(
                if (query.isBlank()) {
                    allItems
                } else {
                    allItems.filter { it.title.lowercase().contains(query) }
                }
            )
            notifyDataSetChanged()
            onVisibleItemsChanged?.invoke(visibleItems)
        }

        fun firstPositionForLabel(label: String): Int? =
            visibleItems.indexOfFirst { indexLabelFor(it) == label }
                .takeIf { it >= 0 }

        private inner class ViewHolder(
            private val binding: ActivityLyricListItemBinding
        ) : RecyclerView.ViewHolder(binding.root) {

            fun bind(file: LyricFile) {
                binding.musicItemAlbum.setImageDrawable(
                    AppCompatResources.getDrawable(
                        binding.root.context,
                        R.drawable.vector_default_lrc
                    )
                )
                binding.musicItemTitle.text = highlightedTitle(file.title)
                binding.musicItemArtist.text = file.folder
                binding.musicItemTitle.setTextColor(themeEngine.currentTheme().itemTextColor)

                binding.root.setOnClickListener { onItemClick(file) }
                binding.root.setOnLongClickListener {
                    onItemLongClick(file)
                    true
                }
            }

            private fun highlightedTitle(title: String): CharSequence {
                val query = searchText
                if (query.isBlank()) return title

                val start = title.lowercase().indexOf(query)
                if (start < 0) return title

                return SpannableString(title).apply {
                    setSpan(
                        ForegroundColorSpan(themeEngine.currentTheme().accentColor),
                        start,
                        start + query.length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    setSpan(
                        StyleSpan(Typeface.BOLD),
                        start,
                        start + query.length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }
        }
    }

    companion object {
        private const val EXTRA_TRACK = "KEY_MUSIC"

        fun intent(context: Context, track: Music): Intent =
            Intent(context, LyricListActivity::class.java).putExtra(EXTRA_TRACK, track)

        fun start(context: Context, track: Music) {
            context.startActivityCompat(intent(context, track))
        }
    }
}
