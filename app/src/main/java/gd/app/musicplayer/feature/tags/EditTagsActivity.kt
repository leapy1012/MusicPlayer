package gd.app.musicplayer.feature.tags

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import gd.app.musicplayer.R
import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.data.repo.EditableTrackMetadata
import gd.app.musicplayer.databinding.ActivityEditTagsBinding
import gd.app.musicplayer.databinding.ItemEditTagFieldBinding
import gd.app.musicplayer.ui.common.base.BaseActivity
import gd.app.musicplayer.ui.common.base.setupEdgeToEdgeToolbar
import gd.app.musicplayer.core.ui.extension.parcelable
import gd.app.musicplayer.core.ui.extension.startActivityCompat
import gd.app.musicplayer.core.util.ToastUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class EditTagsActivity : BaseActivity(), Toolbar.OnMenuItemClickListener {

    private lateinit var binding: ActivityEditTagsBinding
    private lateinit var track: Music

    private lateinit var titleField: EditText
    private lateinit var albumField: EditText
    private lateinit var artistField: EditText
    private lateinit var genreField: EditText

    private val metadataRepo by lazy { appContainer.trackMetadataRepo }

    companion object {
        private const val EXTRA_TRACK = "KEY_MUSIC"

        fun start(context: Context, track: Music) {
            context.startActivityCompat(
                Intent(context, EditTagsActivity::class.java).putExtra(EXTRA_TRACK, track)
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        track = intent.parcelable(EXTRA_TRACK) ?: run {
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

        AlertDialog.Builder(this)
            .setTitle(R.string.edit_tags)
            .setMessage(R.string.edit_tags_interrupt_msg)
            .setPositiveButton(R.string.save) { dialog, _ ->
                dialog.dismiss()
                saveChanges()
            }
            .setNegativeButton(R.string.exit) { dialog, _ ->
                dialog.dismiss()
                finish()
            }
            .show()
    }

    private fun buildFields(container: LinearLayout) {
        titleField = addField(container, R.string.dlg_title, track.title)
        albumField = addField(container, R.string.dlg_album, track.album)
        artistField = addField(container, R.string.dlg_artist, track.artist)
        genreField = addField(container, R.string.dlg_genre, "")
    }

    private fun addField(container: LinearLayout, labelRes: Int, value: String): EditText {
        val binding = ItemEditTagFieldBinding.inflate(layoutInflater, container, true)
        binding.editTagLabel.setText(labelRes)
        binding.editTagValue.setText(value)
        binding.editTagValue.setSelection(binding.editTagValue.text.length)
        return binding.editTagValue
    }

    private fun hasChanges(): Boolean {
        return currentMetadata() != originalMetadata()
    }

    private fun currentMetadata(): EditableTrackMetadata {
        return EditableTrackMetadata(
            title = titleField.text?.toString()?.trim().orEmpty(),
            album = albumField.text?.toString()?.trim().orEmpty(),
            artist = artistField.text?.toString()?.trim().orEmpty(),
            genre = genreField.text?.toString()?.trim().orEmpty()
        )
    }

    private fun originalMetadata(): EditableTrackMetadata {
        return EditableTrackMetadata(
            title = track.title,
            album = track.album,
            artist = track.artist,
            genre = ""
        )
    }

    private fun saveChanges() {
        val metadata = currentMetadata()
        if (metadata.title.isBlank()) {
            titleField.error = getString(R.string.input_error)
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val success = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                metadataRepo.updateTrackMetadata(track, metadata)
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
}
