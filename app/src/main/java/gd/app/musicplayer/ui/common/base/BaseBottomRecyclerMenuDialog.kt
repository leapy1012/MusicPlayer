package gd.app.musicplayer.ui.common.base

import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.ImageViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import gd.app.musicplayer.R
import gd.app.musicplayer.core.ui.drawable.DrawableUtil
import gd.app.musicplayer.feature.theme.applyCurrentTheme

abstract class BaseBottomRecyclerMenuDialog : BottomSheetDialogFragment() {

    protected var recyclerView: RecyclerView? = null
        private set

    protected var rootView: View? = null
        private set

    private var originalRootPaddingLeft = 0
    private var originalRootPaddingTop = 0
    private var originalRootPaddingRight = 0
    private var originalRootPaddingBottom = 0

    protected data class MenuItem(
        val id: Int,
        val iconResId: Int,
        val label: String? = null
    ) {
        companion object {
            fun create(id: Int, iconResId: Int): MenuItem {
                return MenuItem(
                    id = id,
                    iconResId = iconResId
                )
            }
        }

        fun withLabel(text: String): MenuItem = copy(label = text)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return BottomSheetDialog(requireContext(), theme).apply {
            setOnShowListener {
                setupEdgeToEdgeBottomSheet(this)
            }

            window?.let { window ->
                val params = window.attributes
                params.dimAmount = 0.35f
                window.attributes = params
            }
        }
    }

    override fun getTheme(): Int {
        return R.style.ThemeOverlay_MyApp_BottomSheetDialog
    }

    override fun onStart() {
        super.onStart()

        val dialog = dialog as? BottomSheetDialog ?: return
        val window = dialog.window ?: return
        val root = rootView ?: return

        WindowCompat.setDecorFitsSystemWindows(window, false)

        window.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION

        val bottomSheet = dialog.findViewById<View>(
            com.google.android.material.R.id.design_bottom_sheet
        ) ?: return

        val coordinator = dialog.findViewById<View>(
            com.google.android.material.R.id.coordinator
        )

        val container = dialog.findViewById<View>(
            com.google.android.material.R.id.container
        )

        window.decorView.fitsSystemWindows = false
        container?.fitsSystemWindows = false
        coordinator?.fitsSystemWindows = false
        bottomSheet.fitsSystemWindows = false
        root.fitsSystemWindows = false

        bottomSheet.setBackgroundColor(Color.TRANSPARENT)

        /**
         * This is the missing Material BottomSheet part.
         * Without this, Material can keep the sheet above the gesture/nav area.
         */
        dialog.behavior.isGestureInsetBottomIgnored = true
        dialog.behavior.skipCollapsed = true
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED

        val originalLeft = root.paddingLeft
        val originalTop = root.paddingTop
        val originalRight = root.paddingRight
        val originalBottom = root.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { _, insets ->
            val navBottom = insets.getInsets(
                WindowInsetsCompat.Type.navigationBars()
            ).bottom

            /**
             * This makes your black root background continue behind the navigation bar.
             * Your actual menu items stay above because this is padding, not margin.
             */
            root.setPadding(
                originalLeft,
                originalTop,
                originalRight,
                originalBottom + navBottom
            )

            bottomSheet.post {
                dialog.behavior.peekHeight = bottomSheet.height
                dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
            }

            /**
             * Important:
             * Consume the inset here so Material parent containers do not push
             * the sheet back above the navigation bar.
             */
            WindowInsetsCompat.CONSUMED
        }

        ViewCompat.requestApplyInsets(window.decorView)
    }

    private fun setupEdgeToEdgeBottomSheet(dialog: BottomSheetDialog) {
        val window = dialog.window ?: return

        setupWindowForEdgeToEdge(window)

        val bottomSheet = dialog.findViewById<View>(
            com.google.android.material.R.id.design_bottom_sheet
        ) ?: return

        val root = rootView ?: return

        bottomSheet.fitsSystemWindows = false
        root.fitsSystemWindows = false

        // Important: remove default Material background so your root background is visible.
        bottomSheet.setBackgroundColor(Color.TRANSPARENT)

        ViewCompat.setOnApplyWindowInsetsListener(bottomSheet) { sheet, insets ->
            val navBarBottom = insets.getInsets(
                WindowInsetsCompat.Type.navigationBars()
            ).bottom

            /**
             * 1. Add bottom padding to YOUR content root.
             * This makes the black background continue behind the navigation bar.
             */
            root.setPadding(
                originalRootPaddingLeft,
                originalRootPaddingTop,
                originalRootPaddingRight,
                originalRootPaddingBottom + navBarBottom
            )

            /**
             * 2. Force the Material bottom sheet container to include the nav bar area.
             * Without this, the sheet can still stop above the navigation bar.
             */
            sheet.post {
                val behavior = BottomSheetBehavior.from(sheet)

                sheet.measure(
                    View.MeasureSpec.makeMeasureSpec(sheet.width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                )

                behavior.peekHeight = sheet.measuredHeight
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true

                val lp = sheet.layoutParams
                if (lp is CoordinatorLayout.LayoutParams) {
                    lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                    sheet.layoutParams = lp
                }
            }

            insets
        }

        ViewCompat.requestApplyInsets(bottomSheet)
    }

    private fun setupWindowForEdgeToEdge(window: Window) {
        WindowCompat.setDecorFitsSystemWindows(window, false)

        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            window.decorView.systemUiVisibility =
                window.decorView.systemUiVisibility or
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        arguments?.let(::onReadArguments)

        val view = inflater.inflate(
            R.layout.dialog_base_bottom_recycler_menu,
            container,
            false
        )

        rootView = view

        originalRootPaddingLeft = view.paddingLeft
        originalRootPaddingTop = view.paddingTop
        originalRootPaddingRight = view.paddingRight
        originalRootPaddingBottom = view.paddingBottom

        val titleContainer =
            view.findViewById<LinearLayout>(R.id.bottom_recycler_title_container)
        val contentRecyclerView =
            view.findViewById<RecyclerView>(R.id.bottom_recycler_view)
        val bottomContainer =
            view.findViewById<LinearLayout>(R.id.bottom_recycler_bottom_container)

        recyclerView = contentRecyclerView

        onCreateTitleArea(inflater, titleContainer)
        onCreateRecyclerArea(inflater, contentRecyclerView)
        onCreateBottomArea(inflater, bottomContainer)

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        applyCurrentTheme(view)
    }

    override fun onDestroyView() {
        recyclerView = null
        rootView = null
        super.onDestroyView()
    }

    protected fun setRecyclerViewScrollBlocked(blocked: Boolean) {
        recyclerView?.requestDisallowInterceptTouchEvent(blocked)
    }

    protected fun applyDialogItemStyle(view: View, itemContentColor: Int): Boolean {
        return when (view) {
            is TextView -> {
                view.setTextColor(itemContentColor)
                true
            }

            is ImageView -> {
                ImageViewCompat.setImageTintList(
                    view,
                    ColorStateList.valueOf(itemContentColor)
                )
                true
            }

            else -> false
        }
    }

    protected fun applyDialogItemBackground(view: View, pressedColor: Int) {
        view.background = DrawableUtil.rectRipple(Color.TRANSPARENT, pressedColor)
    }

    protected open fun onReadArguments(arguments: Bundle) = Unit

    protected open fun onMenuItemBound(item: MenuItem) = Unit

    protected open fun onCreateBottomArea(
        inflater: LayoutInflater,
        container: LinearLayout
    ) = Unit

    protected open fun onCreateRecyclerArea(
        inflater: LayoutInflater,
        recyclerView: RecyclerView
    ) = Unit

    protected open fun onCreateTitleArea(
        inflater: LayoutInflater,
        container: LinearLayout
    ) = Unit

    protected abstract fun provideMenuItems(): List<MenuItem>

    protected abstract fun onMenuItemClicked(item: MenuItem)
}