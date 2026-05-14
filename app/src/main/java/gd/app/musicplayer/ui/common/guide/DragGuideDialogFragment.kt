package gd.app.musicplayer.ui.common.guide

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.TranslateAnimation
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import dagger.hilt.android.AndroidEntryPoint
import gd.app.musicplayer.R
import gd.app.musicplayer.core.common.extension.dpToPx
import gd.app.musicplayer.core.common.extension.isTablet
import gd.app.musicplayer.core.common.extension.screenWidth
import gd.app.musicplayer.core.designsystem.dialog.BaseDialogFragment

@AndroidEntryPoint
class DragGuideDialogFragment : BaseDialogFragment(), View.OnClickListener {

    private var orientation: Int = ORIENTATION_VERTICAL
    private var guideItem1: View? = null
    private var guideItem2: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        orientation = requireArguments().getInt(ARG_ORIENTATION, ORIENTATION_VERTICAL)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val layoutRes = if (orientation == ORIENTATION_HORIZONTAL) {
            R.layout.fragment_guide_horizontal
        } else {
            R.layout.fragment_guide_vertical
        }
        return inflater.inflate(layoutRes, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.setOnTouchListener { _, _ -> true }

        guideItem1 = view.findViewById(R.id.gide_item_1)
        guideItem2 = view.findViewById(R.id.gide_item_2)

        if (orientation == ORIENTATION_VERTICAL) {
            val trackCountText = resources.getQuantityString(R.plurals.plurals_track, 0, 0)
            view.findViewById<TextView>(R.id.music_item_artist1)?.text = trackCountText
            view.findViewById<TextView>(R.id.music_item_artist2)?.text = trackCountText
        } else {
            applyHorizontalGuideItemSize()
        }

        view.findViewById<View>(R.id.gide_button).setOnClickListener(this)
    }

    override fun onStart() {
        super.onStart()
        clearGuideAnimations()

        if (orientation == ORIENTATION_HORIZONTAL) {
            startHorizontalAnimation()
        } else {
            startVerticalAnimation()
        }
    }

    override fun onStop() {
        clearGuideAnimations()
        super.onStop()
    }

    override fun onClick(v: View?) {
        dismissAllowingStateLoss()
    }

    override fun provideWidth(configuration: Configuration): Int {
        return ViewGroup.LayoutParams.MATCH_PARENT
    }

    override fun provideHeight(configuration: Configuration): Int {
        return ViewGroup.LayoutParams.MATCH_PARENT
    }

    override fun provideDimAmount(): Float = 0f

    override fun shouldAllowOutsideTouchCancel(): Boolean = false

    private fun applyHorizontalGuideItemSize() {
        val context = requireContext()
        val columns = if (context.isTablet()) 6 else 3
        val outerSpacing = context.dpToPx(8f)
        val itemSize = ((context.screenWidth - outerSpacing * (columns + 1)) / columns).coerceAtLeast(0)
        val itemMargin = context.dpToPx(4f)

        listOfNotNull(guideItem1, guideItem2).forEach { item ->
            item.layoutParams = LinearLayout.LayoutParams(itemSize, itemSize).apply {
                leftMargin = itemMargin
                topMargin = itemMargin
                rightMargin = itemMargin
                bottomMargin = itemMargin
            }
        }
    }

    private fun startHorizontalAnimation() {
        val first = guideItem1 ?: return
        val second = guideItem2 ?: return

        first.startAnimation(createTranslateAnimation(fromX = 0f, toX = 1f))
        second.startAnimation(createTranslateAnimation(fromX = 0f, toX = -1f))
    }

    private fun startVerticalAnimation() {
        val first = guideItem1 ?: return
        val second = guideItem2 ?: return

        first.startAnimation(createTranslateAnimation(fromY = 0f, toY = 1f))
        second.startAnimation(createTranslateAnimation(fromY = 0f, toY = -1f))
    }

    private fun createTranslateAnimation(
        fromX: Float = 0f,
        toX: Float = 0f,
        fromY: Float = 0f,
        toY: Float = 0f
    ): TranslateAnimation {
        return TranslateAnimation(
            TranslateAnimation.RELATIVE_TO_SELF,
            fromX,
            TranslateAnimation.RELATIVE_TO_SELF,
            toX,
            TranslateAnimation.RELATIVE_TO_SELF,
            fromY,
            TranslateAnimation.RELATIVE_TO_SELF,
            toY
        ).apply {
            interpolator = AccelerateDecelerateInterpolator()
            repeatCount = TranslateAnimation.INFINITE
            duration = ANIMATION_DURATION_MS
            startOffset = ANIMATION_START_OFFSET_MS
        }
    }

    private fun clearGuideAnimations() {
        guideItem1?.clearAnimation()
        guideItem2?.clearAnimation()
    }

    companion object {
        private const val ARG_ORIENTATION = "orientation"
        private const val ORIENTATION_HORIZONTAL = 1
        private const val ORIENTATION_VERTICAL = 2
        private const val ANIMATION_DURATION_MS = 1_500L
        private const val ANIMATION_START_OFFSET_MS = 2_000L

        fun newHorizontalInstance(): DragGuideDialogFragment {
            return DragGuideDialogFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_ORIENTATION, ORIENTATION_HORIZONTAL)
                }
            }
        }

        fun newVerticalInstance(): DragGuideDialogFragment {
            return DragGuideDialogFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_ORIENTATION, ORIENTATION_VERTICAL)
                }
            }
        }
    }
}
