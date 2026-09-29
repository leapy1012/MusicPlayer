package gd.app.musicplayer.feature.library.options

import android.app.Dialog
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Bundle
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.coui.appcompat.dialog.COUIAlertDialogBuilder
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.formatFileSize
import gd.app.musicplayer.core.common.extension.parcelable
import gd.app.musicplayer.core.common.extension.toDurationString
import gd.app.musicplayer.core.designsystem.dialog.CouiAlertDialogSurface
import gd.app.musicplayer.databinding.DialogMusicDetailBinding
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.ui.tags.EditTagsActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MusicDetailDialogFragment : DialogFragment() {

    private var binding: DialogMusicDetailBinding? = null
    private lateinit var track: Music

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        track = requireArguments().parcelable(ARG_TRACK) ?: error("Missing track")
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val content = DialogMusicDetailBinding.inflate(LayoutInflater.from(requireContext()))
        binding = content

        content.musicEditName.text = track.title
        content.musicEditAlbum.text = track.album.ifBlank { getString(android.R.string.unknownName) }
        content.musicEditArtist.text = track.artist.ifBlank { getString(android.R.string.unknownName) }
        content.musicEditGenre.text = track.genres.ifBlank { unknown() }
        content.tvMusicDetailPath.text = track.data.orEmpty().ifBlank { getString(android.R.string.unknownName) }
        content.tvMusicDetailDuration.text = track.durationMs.toDurationString()
        content.tvMusicDetailSize.text = track.formatFileSize(requireContext())
        content.tvMusicDetailDate.text = formatDetailDate(track.date)
        content.tvMusicDetailBit.text = formatBitRateOrUnknown(track.bitRate)
        content.tvMusicDetailSample.text = formatSampleRateOrUnknown(track.sampleRate)

        val title = requireArguments().getString(ARG_TITLE) ?: getString(R.string.details)
        val builder = COUIAlertDialogBuilder(
            requireContext(),
            com.coui.appcompat.R.style.COUIAlertDialog_Center
        )
            .setTitle(title)
            .setView(content.root)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.equalizer_edit, null)

        val dialog = builder.create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                dismiss()
                EditTagsActivity.start(requireContext(), track)
            }
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setOnClickListener {
                dismiss()
            }
            builder.updateViewAfterShown()
            CouiAlertDialogSurface.apply(dialog)
        }

        loadAudioInfo()
        return dialog
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    private fun loadAudioInfo() {
        if (track.bitRate != -1 && track.sampleRate != -1) return
        val dataSource = track.data ?: return
        lifecycleScope.launch {
            val info = withContext(Dispatchers.IO) { extractAudioInfo(dataSource) }
            if (!isAdded) return@launch
            binding?.tvMusicDetailBit?.text = info.bitRate
            binding?.tvMusicDetailSample?.text = info.sampleRate
        }
    }

    private fun extractAudioInfo(path: String): AudioInfo {
        return runCatching {
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(path)
                val format = (0 until extractor.trackCount)
                    .map(extractor::getTrackFormat)
                    .firstOrNull { it.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
                    ?: return AudioInfo()
                AudioInfo(
                    bitRate = format.getIntegerOrNull(MediaFormat.KEY_BIT_RATE)?.let(::formatBitRateOrUnknown)
                        ?: unknown(),
                    sampleRate = format.getIntegerOrNull(MediaFormat.KEY_SAMPLE_RATE)?.let(::formatSampleRateOrUnknown)
                        ?: unknown()
                )
            } finally {
                extractor.release()
            }
        }.getOrElse { AudioInfo() }
    }

    private fun formatBitRateOrUnknown(bitRate: Int): String {
        if (bitRate <= 0) return unknown()
        return if (bitRate < 1000) "$bitRate bps" else "${bitRate / 1000} kbps"
    }

    private fun formatSampleRateOrUnknown(sampleRate: Int): String {
        return if (sampleRate <= 0) unknown() else "$sampleRate Hz"
    }

    private fun formatDetailDate(epochMillis: Long?): String {
        if (epochMillis == null || epochMillis <= 0L) return unknown()
        val normalizedMillis = if (epochMillis < 1_000_000_000_000L) epochMillis * 1000L else epochMillis
        return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(normalizedMillis))
    }

    private fun unknown(): String = getString(android.R.string.unknownName)

    private data class AudioInfo(
        val bitRate: String = "unknown",
        val sampleRate: String = "unknown"
    )

    private fun MediaFormat.getIntegerOrNull(key: String): Int? {
        return if (containsKey(key)) getInteger(key) else null
    }

    companion object {
        private const val ARG_TRACK = "music"
        private const val ARG_TITLE = "title"

        fun newInstance(track: Music, title: String? = null): MusicDetailDialogFragment {
            return MusicDetailDialogFragment().apply {
                arguments = bundleOf(
                    ARG_TRACK to track,
                    ARG_TITLE to title
                )
            }
        }
    }
}
