package gd.app.musicplayer.feature.library.artwork

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.parcelable
import gd.app.musicplayer.core.designsystem.dialog.BaseBottomSheetDialogFragment
import gd.app.musicplayer.core.common.util.ToastUtil
import gd.app.musicplayer.domain.model.ArtworkRequest
import gd.app.musicplayer.domain.model.MusicSet
import gd.app.musicplayer.domain.repository.ArtworkRepo
import gd.app.musicplayer.databinding.DialogManageArtworkBinding
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ManageArtworkDialogFragment : BaseBottomSheetDialogFragment(), View.OnClickListener {

    @Inject lateinit var artworkRepo: ArtworkRepo

    private var _binding: DialogManageArtworkBinding? = null
    private val binding get() = _binding!!

    private lateinit var request: ArtworkRequest
    private var trackAlbumArtworkPath: String? = null
    private var applyToAll = false

    private val galleryPicker =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri == null) return@registerForActivityResult
            val context = context ?: return@registerForActivityResult
            val pendingFile = ArtworkImageStore.createManagedArtworkFile(context)
            cropLauncher.launch(
                ArtworkCropActivity.intent(context, uri, pendingFile.absolutePath)
            )
        }

    private val cropLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val path = result.data?.getStringExtra(ArtworkCropActivity.RESULT_ARTWORK_PATH)
                ?: return@registerForActivityResult
            applyArtwork(path)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        request = requireArguments().parcelable(ARG_REQUEST)
            ?: error("Missing artwork request")
        applyToAll = requireArguments().getBoolean(ARG_DEFAULT_APPLY_TO_ALL, false)

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.albumFromReset.setOnClickListener(this)
        binding.albumFromGallery.setOnClickListener(this)
        binding.albumFromAlbumArtwork.setOnClickListener(this)
        binding.albumApplyAll.setOnClickListener(this)

        renderApplyAll()
        loadTrackAlbumArtwork()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    override fun onCreateBottomSheetView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogManageArtworkBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.album_apply_all -> {
                applyToAll = !applyToAll
                renderApplyAll()
            }

            R.id.album_from_reset -> {
                applyArtwork(null)
            }

            R.id.album_from_album_artwork -> {
                val artworkPath = trackAlbumArtworkPath ?: return
                applyArtwork(artworkPath)
            }

            R.id.album_from_gallery -> {
                galleryPicker.launch("image/*")
            }
        }
    }

    private fun renderApplyAll() {
        val set = (request as? ArtworkRequest.MusicSetTarget)?.musicSet
        val supportsApplyAll = set is MusicSet.Album || set is MusicSet.Artist || set is MusicSet.Genre

        binding.albumApplyAll.isVisible = supportsApplyAll
        binding.applyAllCheckbox.isSelected = applyToAll
        binding.albumApplyAllType.text = when (set) {
            is MusicSet.Album -> getString(R.string.album_apply_all_album)
            is MusicSet.Artist -> getString(R.string.album_apply_all_artist)
            is MusicSet.Genre -> getString(R.string.album_apply_all_genre)
            else -> null
        }
    }

    private fun loadTrackAlbumArtwork() {
        val track = (request as? ArtworkRequest.Track)?.music ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            trackAlbumArtworkPath = artworkRepo.getCollectionArtwork(
                sourceId = MusicSet.ALBUMS,
                sourceName = track.album
            )

            binding.albumFromAlbumArtwork.isVisible = !trackAlbumArtworkPath.isNullOrBlank()
        }
    }

    private fun applyArtwork(path: String?) {
        val appContext = context ?: return
        lifecycleScope.launch {
            when (val current = request) {
                is ArtworkRequest.Track -> {
                    artworkRepo.updateTrackArtwork(current.music, path)
                }

                is ArtworkRequest.MusicSetTarget -> {
                    artworkRepo.updateSetArtwork(
                        musicSet = current.musicSet,
                        artworkPath = path,
                        applyToAll = applyToAll
                    )
                }
            }
            parentFragmentManager.setFragmentResult(
                RESULT_KEY_ARTWORK_APPLIED,
                Bundle().apply {
                    putParcelable(RESULT_REQUEST, request)
                    putString(RESULT_ARTWORK_PATH, path)
                }
            )
            ToastUtil.show(appContext, R.string.succeed)
            dismissAllowingStateLoss()
        }
    }

    companion object {
        private const val ARG_REQUEST = "artwork_request"
        private const val ARG_DEFAULT_APPLY_TO_ALL = "default_apply_to_all"
        const val RESULT_KEY_ARTWORK_APPLIED = "result_artwork_applied"
        const val RESULT_REQUEST = "result_request"
        const val RESULT_ARTWORK_PATH = "result_artwork_path"

        fun newInstance(
            request: ArtworkRequest,
            defaultApplyToAll: Boolean = false
        ): ManageArtworkDialogFragment {
            return ManageArtworkDialogFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_REQUEST, request)
                    putBoolean(ARG_DEFAULT_APPLY_TO_ALL, defaultApplyToAll)
                }
            }
        }
    }
}
