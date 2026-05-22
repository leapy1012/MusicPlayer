package gd.app.musicplayer.feature.widget

import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.widget.ImageViewCompat
import androidx.fragment.app.FragmentManager
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import gd.app.lib.view.square.MeasurePolicyFactory
import gd.app.lib.view.square.SquareRoundedFrameLayout
import gd.app.musicplayer.R
import gd.app.musicplayer.core.designsystem.dialog.BaseDialogFragment
import gd.app.musicplayer.core.designsystem.theme.accentColor
import gd.app.musicplayer.core.designsystem.theme.dialogTitleColor
import gd.app.musicplayer.databinding.DialogWidgetBinding
import gd.app.musicplayer.databinding.DialogWidgetItemBinding

class WidgetManualAddDialogFragment : BaseDialogFragment(),
    View.OnClickListener,
    ViewPager.OnPageChangeListener {

    private var binding: DialogWidgetBinding? = null
    private val pages = listOf(
        WidgetManualAddPage(R.string.dlg_add_widget_tips_1, R.drawable.widget_tips_01),
        WidgetManualAddPage(R.string.dlg_add_widget_tips_2, R.drawable.widget_tips_02),
        WidgetManualAddPage(R.string.dlg_add_widget_tips_3, R.drawable.widget_tips_03),
        WidgetManualAddPage(R.string.dlg_add_widget_tips_4, R.drawable.widget_tips_04)
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val viewBinding = DialogWidgetBinding.inflate(inflater, container, false)
        binding = viewBinding
        viewBinding.root.background = provideBackgroundDrawable()
        updateDialogRatio(resources.configuration)
        viewBinding.dialogWidgetLeft.setOnClickListener(this)
        viewBinding.dialogWidgetRight.setOnClickListener(this)
        viewBinding.dialogWidgetPager.adapter = WidgetTipsPagerAdapter(inflater, pages)
        viewBinding.dialogWidgetPager.addOnPageChangeListener(this)
        setupIndicators(viewBinding)
        onPageSelected(0)
        return viewBinding.root
    }

    override fun provideBackgroundDrawable(): Drawable {
        return currentTheme().getDialogSurfaceDrawable(requireContext())
    }

    override fun onDestroyView() {
        binding?.dialogWidgetPager?.removeOnPageChangeListener(this)
        binding = null
        super.onDestroyView()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateDialogRatio(newConfig)
    }

    override fun onClick(view: View) {
        val current = binding?.dialogWidgetPager?.currentItem ?: return
        when (view.id) {
            R.id.dialog_widget_left -> {
                if (current > 0) binding?.dialogWidgetPager?.currentItem = current - 1
            }
            R.id.dialog_widget_right -> {
                if (current < pages.lastIndex) binding?.dialogWidgetPager?.currentItem = current + 1
            }
        }
    }

    override fun onPageSelected(position: Int) {
        val viewBinding = binding ?: return
        for (index in 0 until viewBinding.dialogWidgetIndicator.childCount) {
            viewBinding.dialogWidgetIndicator.getChildAt(index).isSelected = index == position
        }
        viewBinding.tips.setText(pages[position].textRes)
        viewBinding.dialogWidgetLeft.isEnabled = position > 0
        viewBinding.dialogWidgetRight.isEnabled = position < pages.lastIndex
    }

    override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) = Unit

    override fun onPageScrollStateChanged(state: Int) = Unit

    override fun onThemeChanged(palette: gd.app.musicplayer.core.designsystem.theme.ThemePalette?) {
        super.onThemeChanged(palette)
        binding?.root?.background = provideBackgroundDrawable()
        binding?.let(::tintIndicators)
    }

    private fun setupIndicators(viewBinding: DialogWidgetBinding) {
        val size = resources.getDimensionPixelSize(R.dimen.widget_manual_add_indicator_size)
        val marginStart = resources.getDimensionPixelSize(R.dimen.widget_manual_add_indicator_margin)
        repeat(pages.size) { index ->
            val indicator = ImageView(requireContext()).apply {
                setImageResource(R.drawable.shape_indicator_circle)
                scaleType = ImageView.ScaleType.CENTER
            }
            val params = LinearLayout.LayoutParams(size, size).apply {
                if (index > 0) leftMargin = marginStart
            }
            viewBinding.dialogWidgetIndicator.addView(indicator, params)
        }
        tintIndicators(viewBinding)
    }

    private fun tintIndicators(viewBinding: DialogWidgetBinding) {
        val colors = currentTheme().let { palette ->
            android.content.res.ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_selected), intArrayOf()),
                intArrayOf(palette.accentColor, palette.dialogTitleColor)
            )
        }
        for (index in 0 until viewBinding.dialogWidgetIndicator.childCount) {
            ImageViewCompat.setImageTintList(
                viewBinding.dialogWidgetIndicator.getChildAt(index) as ImageView,
                colors
            )
        }
    }

    private fun updateDialogRatio(configuration: Configuration) {
        val ratio = if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            LANDSCAPE_RATIO
        } else {
            PORTRAIT_RATIO
        }
        (binding?.root as? SquareRoundedFrameLayout)?.setSquare(
            MeasurePolicyFactory.create(
                mode = MeasurePolicyFactory.MODE_HEIGHT_FROM_WIDTH,
                ratio = ratio
            )
        )
    }

    private data class WidgetManualAddPage(
        val textRes: Int,
        val imageRes: Int
    )

    private class WidgetTipsPagerAdapter(
        private val inflater: LayoutInflater,
        private val pages: List<WidgetManualAddPage>
    ) : PagerAdapter() {
        override fun getCount(): Int = pages.size

        override fun isViewFromObject(view: View, obj: Any): Boolean = view == obj

        override fun instantiateItem(container: ViewGroup, position: Int): Any {
            val binding = DialogWidgetItemBinding.inflate(inflater, container, false)
            binding.image.setImageResource(pages[position].imageRes)
            container.addView(binding.root)
            return binding.root
        }

        override fun destroyItem(container: ViewGroup, position: Int, obj: Any) {
            container.removeView(obj as View)
        }
    }

    companion object {
        private const val TAG = "WidgetManualAddDialogFragment"
        private const val PORTRAIT_RATIO = 1.56f
        private const val LANDSCAPE_RATIO = 0.5625f

        fun show(fragmentManager: FragmentManager) {
            if (fragmentManager.findFragmentByTag(TAG) != null) return
            WidgetManualAddDialogFragment().show(fragmentManager, TAG)
        }
    }
}
