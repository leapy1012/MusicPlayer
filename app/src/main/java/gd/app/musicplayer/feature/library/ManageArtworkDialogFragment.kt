package gd.app.musicplayer.feature.library

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import gd.app.musicplayer.R
import gd.app.musicplayer.core.ui.extension.appContainer
import gd.app.musicplayer.core.ui.extension.parcelable
import gd.app.musicplayer.core.theme.*
import gd.app.musicplayer.core.ui.drawable.DrawableUtil
import gd.app.musicplayer.core.util.ToastUtil
import gd.app.musicplayer.data.model.ArtworkRequest
import gd.app.musicplayer.data.model.MusicSet
import gd.app.musicplayer.databinding.DialogManageArtworkBinding
import kotlinx.coroutines.launch

class ManageArtworkDialogFragment : BottomSheetDialogFragment(), View.OnClickListener {

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
            dismiss()
            applyArtwork(path)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        request = requireArguments().parcelable(ARG_REQUEST)
            ?: error("Missing artwork request")

    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogManageArtworkBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        applyDialogBackground(binding.root)
        applyTagStyles(binding.sheetContent)

        binding.albumFromReset.setOnClickListener(this)
        binding.albumFromGallery.setOnClickListener(this)
        binding.albumFromAlbumArtwork.setOnClickListener(this)
        binding.albumApplyAll.setOnClickListener(this)

        applyActionStyling()
        renderApplyAll()
        loadTrackAlbumArtwork()

//        binding.sheetContent.setBackgroundColor(android.graphics.Color.RED)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.album_apply_all -> {
                applyToAll = !applyToAll
                renderApplyAll()
            }

            R.id.album_from_reset -> {
                dismiss()
                applyArtwork(null)
            }

            R.id.album_from_album_artwork -> {
                val artworkPath = trackAlbumArtworkPath ?: return
                dismiss()
                applyArtwork(artworkPath)
            }

            R.id.album_from_gallery -> {
                galleryPicker.launch("image/*")
            }
        }
    }

    private fun renderApplyAll() {
        val set = (request as? ArtworkRequest.MusicSetTarget)?.musicSet
        val supportsApplyAll =
            set is MusicSet.Album || set is MusicSet.Artist || set is MusicSet.Genre

        binding.albumApplyAll.isVisible = supportsApplyAll
        binding.applyAllCheckbox.isSelected = applyToAll
        binding.albumApplyAllType.text = when (set) {
            is MusicSet.Album -> getString(R.string.album_apply_all_album)
            is MusicSet.Artist -> getString(R.string.album_apply_all_artist)
            is MusicSet.Genre -> getString(R.string.album_apply_all_genre)
            else -> null
        }
    }

    private fun applyActionStyling() {
        val palette = requireContext().appContainer.themeRepo
            .getCorePalette(requireContext())
        val actionViews = listOf(
            binding.dialogTitle,
            binding.albumFromReset,
            binding.albumFromGallery,
            binding.albumFromAlbumArtwork,
            binding.albumApplyAllType
        )
        actionViews.forEach { view ->
            view.setTextColor(palette.titleColor)
        }

        val clickableRows = listOf(
            binding.albumFromReset,
            binding.albumFromGallery,
            binding.albumFromAlbumArtwork,
            binding.albumApplyAll
        )
        clickableRows.forEach { row ->
            row.background = DrawableUtil.rectRipple(
                fillColor = Color.TRANSPARENT,
                rippleColor = palette.rippleColor
            )
        }
    }

    private fun applyDialogBackground(rootView: View) {
        rootView.background = rootView.context.appContainer.themeRepo
            .getCorePalette(rootView.context)
            .getDialogSurfaceDrawable(rootView.context)
    }

    private fun applyTagStyles(rootView: View) {
        val accentColor = requireContext().appContainer.themeRepo.getAccentColor(requireContext())
        val palette = rootView.context.appContainer.themeRepo.getCorePalette(rootView.context)

        val titleColor = palette.titleColor
        val messageColor = palette.messageColor
        val rippleColor = palette.rippleColor
        val selectBoxNormalColor =
            if (titleColor == Color.WHITE) -2171170 else -3355444

        fun apply(view: View) {
            when (view.tag as? String) {
                "dialogTitle", "dialogTitleColor", "dialogTitleIcon", "dialogItem" -> {
                    when (view) {
                        is TextView -> view.setTextColor(titleColor)
                        is ImageView -> view.imageTintList = ColorStateList.valueOf(titleColor)
                    }
                }

                "dialogMessage", "dialogMessageColor" -> {
                    when (view) {
                        is TextView -> view.setTextColor(messageColor)
                        is ImageView -> view.imageTintList = ColorStateList.valueOf(messageColor)
                    }
                }

                "dialogItemBackground" -> {
                    view.background = DrawableUtil.rectRipple(
                        fillColor = Color.TRANSPARENT,
                        rippleColor = rippleColor
                    )
                }

                "dialogSelectBox" -> {
                    if (view is ImageView) {
                        view.imageTintList = ColorStateList(
                            arrayOf(
                                intArrayOf(android.R.attr.state_selected),
                                intArrayOf(android.R.attr.state_checked),
                                intArrayOf(android.R.attr.state_activated),
                                intArrayOf()
                            ),
                            intArrayOf(
                                accentColor,
                                accentColor,
                                accentColor,
                                selectBoxNormalColor
                            )
                        )
                    }
                }
            }

            if (view is ViewGroup) {
                for (index in 0 until view.childCount) {
                    apply(view.getChildAt(index))
                }
            }
        }

        apply(rootView)
    }

    private fun loadTrackAlbumArtwork() {
        val track = (request as? ArtworkRequest.Track)?.music ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            trackAlbumArtworkPath =
                requireContext().appContainer.artworkRepo.getCollectionArtwork(
                    sourceId = MusicSet.ALBUMS_ID,
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
                    appContext.appContainer.artworkRepo.updateTrackArtwork(current.music, path)
                }

                is ArtworkRequest.MusicSetTarget -> {
                    appContext.appContainer.artworkRepo.updateSetArtwork(
                        musicSet = current.musicSet,
                        artworkPath = path,
                        applyToAll = applyToAll
                    )
                }
            }
            ToastUtil.show(appContext, R.string.succeed)
        }
    }

    companion object {
        private const val ARG_REQUEST = "artwork_request"

        fun newInstance(request: ArtworkRequest): ManageArtworkDialogFragment {
            return ManageArtworkDialogFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_REQUEST, request)
                }
            }
        }
    }
}
