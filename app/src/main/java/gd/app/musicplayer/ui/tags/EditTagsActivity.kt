package gd.app.musicplayer.ui.tags

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.MenuItem
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.loadMusicArtwork
import gd.app.musicplayer.core.common.extension.parcelable
import gd.app.musicplayer.core.common.extension.startActivityCompat
import gd.app.musicplayer.core.common.util.ToastUtil
import gd.app.musicplayer.core.designsystem.dialog.createMessageDialogConfig
import gd.app.musicplayer.core.designsystem.dialog.showMessageDialog
import gd.app.musicplayer.domain.model.ArtworkRequest
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.domain.model.MusicSet
import gd.app.musicplayer.domain.repository.EditableAlbumMetadata
import gd.app.musicplayer.domain.repository.EditableTrackMetadata
import gd.app.musicplayer.databinding.ActivityEditTagsBinding
import gd.app.musicplayer.databinding.ItemEditTagFieldBinding
import gd.app.musicplayer.ui.common.base.BaseActivity
import gd.app.musicplayer.ui.common.base.setupEdgeToEdgeToolbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.core.common.extension.albumArtSource
import gd.app.musicplayer.domain.repository.MusicSetMetadataRepo
import gd.app.musicplayer.domain.repository.TrackMetadataRepo
import gd.app.musicplayer.ui.library.artwork.ManageArtworkDialogFragment
import javax.inject.Inject

@AndroidEntryPoint
class EditTagsActivity : BaseActivity(), Toolbar.OnMenuItemClickListener {

    private lateinit var binding: ActivityEditTagsBinding
    private var track: Music? = null
    private var musicSet: MusicSet? = null

    private lateinit var titleField: EditText
    private var albumField: EditText? = null
    private var artistField: EditText? = null
    private var genreField: EditText? = null
    private var yearField: EditText? = null
    private var trackNumberField: EditText? = null
    private var coverView: ImageView? = null

    private var dirty = false
    private var currentTrackCoverPath: String? = null
    private var currentSetCoverPath: String? = null

    @Inject lateinit var metadataRepo: TrackMetadataRepo

    @Inject lateinit var musicSetMetadataRepo: MusicSetMetadataRepo

    companion object {
        private const val EXTRA_TRACK = "KEY_MUSIC"
        private const val EXTRA_MUSIC_SET = "KEY_MUSIC_SET"

        fun start(context: Context, track: Music) {
            context.startActivityCompat(
                Intent(context, EditTagsActivity::class.java).putExtra(EXTRA_TRACK, track)
            )
        }

        fun start(context: Context, musicSet: MusicSet) {
            context.startActivityCompat(
                Intent(context, EditTagsActivity::class.java).putExtra(EXTRA_MUSIC_SET, musicSet)
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        track = intent.parcelable(EXTRA_TRACK)
        musicSet = intent.parcelable(EXTRA_MUSIC_SET)
        if (track == null && musicSet == null) {
            finish()
            return
        }

        binding = ActivityEditTagsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupEdgeToEdgeToolbar(
            root = binding.root,
            statusBarView = binding.statusBarSpace,
            toolbar = binding.toolbar,
            titleRes = R.string.edit_tags
        )
        binding.toolbar.inflateMenu(R.menu.menu_activity_edit_tags)
        binding.toolbar.setOnMenuItemClickListener(this)

        registerArtworkResultListener()
        buildFields(binding.editTagsContainer)
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        if (item.itemId != R.id.menu_save) return false
        if (!hasChanges()) {
            ToastUtil.show(this, R.string.audio_editor_succeed)
            finish()
            return true
        }
        saveChanges()
        return true
    }

    override fun onBackPressed() {
        if (!hasChanges()) {
            super.onBackPressed()
            return
        }

        showMessageDialog(
            createMessageDialogConfig(
                title = getString(R.string.edit_tags),
                message = getString(R.string.edit_tags_interrupt_msg),
                positiveText = getString(R.string.save),
                negativeText = getString(R.string.exit),
                positiveClickListener = { dialog, _ ->
                    dialog.dismiss()
                    saveChanges()
                },
                negativeClickListener = { dialog, _ ->
                    dialog.dismiss()
                    finish()
                }
            )
        )
    }

    private fun buildFields(container: LinearLayout) {
        when (val currentTrack = track) {
            null -> buildSetFields(container)
            else -> buildTrackFields(container, currentTrack)
        }
    }

    private fun buildTrackFields(container: LinearLayout, currentTrack: Music) {
        layoutInflater.inflate(R.layout.layout_tag_edit_music, container, true)

        coverView = container.findViewById<ImageView>(R.id.music_edit_cover).also { image ->
            currentTrackCoverPath = currentTrack.albumPicture
            image.loadMusicArtwork(currentTrack.albumArtSource())
            image.setOnClickListener {
                ManageArtworkDialogFragment
                    .newInstance(ArtworkRequest.Track(currentTrack), defaultApplyToAll = false)
                    .show(supportFragmentManager, ManageArtworkDialogFragment::class.java.simpleName)
            }
        }

        titleField = container.findViewById(R.id.music_edit_name)
        albumField = container.findViewById(R.id.music_edit_album)
        artistField = container.findViewById(R.id.music_edit_artist)
        genreField = container.findViewById(R.id.music_edit_genre)
        trackNumberField = container.findViewById(R.id.music_edit_track_number)

        titleField.setText(currentTrack.title)
        albumField?.setText(currentTrack.album)
        artistField?.setText(currentTrack.artist)
        genreField?.setText("")
        trackNumberField?.setText("")

        bindDirtyWatcher(titleField)
        bindDirtyWatcher(albumField)
        bindDirtyWatcher(artistField)
        bindDirtyWatcher(genreField)
        bindDirtyWatcher(trackNumberField)
    }

    private fun buildSetFields(container: LinearLayout) {
        layoutInflater.inflate(R.layout.layout_tag_edit_image, container, true)
        coverView = container.findViewById<ImageView>(R.id.music_edit_cover)

        val set = musicSet ?: run {
            finish()
            return
        }

        currentSetCoverPath = set.albumArt
        loadSetCover(set, set.albumArt)

        coverView?.setOnClickListener {
            ManageArtworkDialogFragment
                .newInstance(ArtworkRequest.MusicSetTarget(set), defaultApplyToAll = true)
                .show(supportFragmentManager, ManageArtworkDialogFragment::class.java.simpleName)
        }

        when (set) {
            is MusicSet.Album -> {
                titleField = addField(container, R.string.dlg_album, set.name)
                artistField = addField(container, R.string.dlg_artist, set.artist)
                genreField = addField(container, R.string.dlg_genre, set.genres)
                yearField = addField(container, R.string.dlg_year, if (set.year > 0) set.year.toString() else "")
                yearField?.inputType = android.text.InputType.TYPE_CLASS_NUMBER

                bindDirtyWatcher(titleField)
                bindDirtyWatcher(artistField)
                bindDirtyWatcher(genreField)
                bindDirtyWatcher(yearField)
            }

            is MusicSet.Artist -> {
                titleField = addField(container, R.string.dlg_artist, set.name)
                bindDirtyWatcher(titleField)
            }

            is MusicSet.Genre -> {
                titleField = addField(container, R.string.dlg_genre, set.name)
                bindDirtyWatcher(titleField)
            }

            else -> finish()
        }
    }

    private fun addField(container: LinearLayout, labelRes: Int, value: String): EditText {
        val itemBinding = ItemEditTagFieldBinding.inflate(layoutInflater, container, true)
        itemBinding.editTagLabel.setText(labelRes)
        itemBinding.editTagValue.setText(value)
        itemBinding.editTagValue.setSelection(itemBinding.editTagValue.text.length)
        return itemBinding.editTagValue
    }

    private fun bindDirtyWatcher(editText: EditText?) {
        editText?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                dirty = true
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
    }

    private fun hasChanges(): Boolean {
        val trackModel = track
        if (trackModel != null) {
            val unchangedText = currentTrackMetadata() == originalTrackMetadata(trackModel)
            val unchangedCover = currentTrackCoverPath == trackModel.albumPicture
            return dirty || !unchangedText || !unchangedCover
        }

        val setModel = musicSet ?: return false
        val changedFields = when (setModel) {
            is MusicSet.Album -> {
                val year = yearField?.text?.toString()?.trim()?.toIntOrNull() ?: 0
                titleField.text?.toString()?.trim().orEmpty() != setModel.name ||
                    artistField?.text?.toString()?.trim().orEmpty() != setModel.artist ||
                    genreField?.text?.toString()?.trim().orEmpty() != setModel.genres ||
                    year != setModel.year
            }
            is MusicSet.Artist -> titleField.text?.toString()?.trim().orEmpty() != setModel.name
            is MusicSet.Genre -> titleField.text?.toString()?.trim().orEmpty() != setModel.name
            else -> false
        }
        val changedCover = currentSetCoverPath != setModel.albumArt
        return dirty || changedFields || changedCover
    }

    private fun currentTrackMetadata(): EditableTrackMetadata {
        return EditableTrackMetadata(
            title = titleField.text?.toString()?.trim().orEmpty(),
            album = albumField?.text?.toString()?.trim().orEmpty(),
            artist = artistField?.text?.toString()?.trim().orEmpty(),
            genre = genreField?.text?.toString()?.trim().orEmpty(),
            track = trackNumberField?.text?.toString()?.trim()?.toIntOrNull() ?: 0
        )
    }

    private fun originalTrackMetadata(track: Music): EditableTrackMetadata {
        return EditableTrackMetadata(
            title = track.title,
            album = track.album,
            artist = track.artist,
            genre = "",
            track = 0
        )
    }

    private fun saveChanges() {
        if (track != null) {
            saveTrackChanges()
            return
        }
        saveSetChanges()
    }

    private fun saveTrackChanges() {
        val metadata = currentTrackMetadata()
        if (metadata.title.isBlank() || metadata.album.isBlank() || metadata.artist.isBlank() || metadata.genre.isBlank()) {
            ToastUtil.show(this, R.string.equalizer_edit_input_error)
            return
        }
        val currentTrack = track ?: return

        lifecycleScope.launch(Dispatchers.IO) {
            val success = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                metadataRepo.updateTrackMetadata(currentTrack, metadata)
            } else {
                false
            }
            launch(Dispatchers.Main) {
                if (success) {
                    ToastUtil.show(this@EditTagsActivity, R.string.audio_editor_succeed)
                    finish()
                } else {
                    ToastUtil.show(this@EditTagsActivity, R.string.feature_not_implemented)
                }
            }
        }
    }

    private fun saveSetChanges() {
        val currentSet = musicSet ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val success = when (currentSet) {
                is MusicSet.Album -> {
                    val album = titleField.text?.toString()?.trim().orEmpty()
                    if (album.isBlank()) {
                        launch(Dispatchers.Main) { ToastUtil.show(this@EditTagsActivity, R.string.equalizer_edit_input_error) }
                        return@launch
                    }
                    musicSetMetadataRepo.updateAlbumMetadata(
                        currentSet,
                        EditableAlbumMetadata(
                            album = album,
                            artist = artistField?.text?.toString()?.trim().orEmpty(),
                            genre = genreField?.text?.toString()?.trim().orEmpty(),
                            year = yearField?.text?.toString()?.toIntOrNull() ?: 0
                        ),
                        artworkPath = currentSetCoverPath
                    )
                }

                is MusicSet.Artist -> {
                    val newName = titleField.text?.toString()?.trim().orEmpty()
                    if (newName.isBlank()) {
                        launch(Dispatchers.Main) { ToastUtil.show(this@EditTagsActivity, R.string.equalizer_edit_input_error) }
                        return@launch
                    }
                    musicSetMetadataRepo.updateArtistMetadata(
                        set = currentSet,
                        newName = newName,
                        artworkPath = currentSetCoverPath
                    )
                }

                is MusicSet.Genre -> {
                    val newName = titleField.text?.toString()?.trim().orEmpty()
                    if (newName.isBlank()) {
                        launch(Dispatchers.Main) { ToastUtil.show(this@EditTagsActivity, R.string.equalizer_edit_input_error) }
                        return@launch
                    }
                    musicSetMetadataRepo.updateGenreMetadata(
                        set = currentSet,
                        newName = newName,
                        artworkPath = currentSetCoverPath
                    )
                }

                else -> false
            }
            launch(Dispatchers.Main) {
                if (success) {
                    ToastUtil.show(this@EditTagsActivity, R.string.audio_editor_succeed)
                    finish()
                } else {
                    ToastUtil.show(this@EditTagsActivity, R.string.equalizer_edit_input_error)
                }
            }
        }
    }

    private fun registerArtworkResultListener() {
        supportFragmentManager.setFragmentResultListener(
            ManageArtworkDialogFragment.RESULT_KEY_ARTWORK_APPLIED,
            this
        ) { _, bundle ->
            val request = bundle.parcelable<ArtworkRequest>(ManageArtworkDialogFragment.RESULT_REQUEST) ?: return@setFragmentResultListener
            val artworkPath = bundle.getString(ManageArtworkDialogFragment.RESULT_ARTWORK_PATH)
            when (request) {
                is ArtworkRequest.Track -> {
                    val currentTrack = track ?: return@setFragmentResultListener
                    if (request.music.id != currentTrack.id) return@setFragmentResultListener
                    currentTrackCoverPath = artworkPath
                    coverView?.let {
                        if (artworkPath.isNullOrBlank()) {
                            it.loadMusicArtwork(currentTrack.albumArtSource())
                        } else {
                            Glide.with(this).load(artworkPath).error(R.drawable.default_album_identify).into(it)
                        }
                    }
                    dirty = true
                }

                is ArtworkRequest.MusicSetTarget -> {
                    val currentSet = musicSet ?: return@setFragmentResultListener
                    if (request.musicSet.id != currentSet.id || request.musicSet.name != currentSet.name) {
                        return@setFragmentResultListener
                    }
                    currentSetCoverPath = artworkPath
                    loadSetCover(currentSet, artworkPath)
                    dirty = true
                }
            }
        }
    }

    private fun loadSetCover(set: MusicSet, artworkPath: String?) {
        val fallback = when (set) {
            is MusicSet.Album -> R.drawable.album_large
            is MusicSet.Artist -> R.drawable.artist_large
            is MusicSet.Genre -> R.drawable.genre_large
            else -> R.drawable.default_album_identify
        }
        coverView?.let { image ->
            val source = artworkPath?.takeIf { it.isNotBlank() }
            if (source == null) {
                image.setImageResource(fallback)
                return
            }
            Glide.with(this)
                .load(source)
                .error(fallback)
                .into(image)
        }
    }
}
