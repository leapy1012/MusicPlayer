package gd.app.musicplayer.ui.library.options

import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.formatFileSize
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.databinding.DialogMusicDetailBinding
import gd.app.musicplayer.core.designsystem.dialog.BaseDialogFragment
import gd.app.musicplayer.ui.tags.EditTagsActivity
import gd.app.musicplayer.core.common.extension.parcelable
import gd.app.musicplayer.core.common.extension.toDurationString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MusicDetailDialogFragment : BaseDialogFragment(), View.OnClickListener {

    private var _binding: DialogMusicDetailBinding? = null
    private val binding: DialogMusicDetailBinding
        get() = checkNotNull(_binding)

    private lateinit var track: Music

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        track = requireArguments().parcelable(ARG_TRACK) ?: error("Missing track")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogMusicDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        applyDialogWidth(0.9f)
        applyDialogBackground(binding.root)

        binding.dialogTitle.text = requireArguments().getString(ARG_TITLE) ?: getString(R.string.details)
        binding.dialogButtonCancel.setOnClickListener(this)
        binding.dialogButtonEdit.setOnClickListener(this)

        binding.musicEditName.text = track.title
        binding.musicEditAlbum.text = track.album.ifBlank { getString(android.R.string.unknownName) }
        binding.musicEditArtist.text = track.artist.ifBlank { getString(android.R.string.unknownName) }
        binding.musicEditGenre.text = track.genres.ifBlank { unknown() }
        binding.tvMusicDetailPath.text = track.data.orEmpty().ifBlank { getString(android.R.string.unknownName) }
        binding.tvMusicDetailDuration.text = track.durationMs.toDurationString()
        binding.tvMusicDetailSize.text = track.formatFileSize(requireContext())
        binding.tvMusicDetailDate.text = formatDetailDate(track.date)
        binding.tvMusicDetailBit.text = formatBitRateOrUnknown(track.bitRate)
        binding.tvMusicDetailSample.text = formatSampleRateOrUnknown(track.sampleRate)

        loadAudioInfo()
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.dialog_button_edit -> {
                dismiss()
                EditTagsActivity.start(requireContext(), track)
            }

            R.id.dialog_button_cancel -> dismiss()
        }
    }

    private fun loadAudioInfo() {
        if (track.bitRate != -1 && track.sampleRate != -1) return
        val dataSource = track.data ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val info = withContext(Dispatchers.IO) { extractAudioInfo(dataSource) }
            binding.tvMusicDetailBit.text = info.bitRate
            binding.tvMusicDetailSample.text = info.sampleRate
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

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

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

