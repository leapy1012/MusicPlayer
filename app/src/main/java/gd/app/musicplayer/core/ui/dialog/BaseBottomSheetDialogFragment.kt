package gd.app.musicplayer.core.ui.dialog

import android.app.Dialog
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import gd.app.musicplayer.R

abstract class BaseBottomSheetDialogFragment : BottomSheetDialogFragment() {

    protected var rootView: View? = null
        private set

    private var originalRootPaddingLeft = 0
    private var originalRootPaddingTop = 0
    private var originalRootPaddingRight = 0
    private var originalRootPaddingBottom = 0

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
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = onCreateBottomSheetView(inflater, container, savedInstanceState)

        rootView = view

        originalRootPaddingLeft = view.paddingLeft
        originalRootPaddingTop = view.paddingTop
        originalRootPaddingRight = view.paddingRight
        originalRootPaddingBottom = view.paddingBottom

        return view
    }

    private fun setupEdgeToEdgeBottomSheet(dialog: BottomSheetDialog) {
        val window = dialog.window ?: return
        val root = rootView ?: return

        setupWindowForEdgeToEdge(window)

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

        dialog.behavior.isGestureInsetBottomIgnored = true
        dialog.behavior.skipCollapsed = true
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED

        ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { _, insets ->
            val navBottom = insets.getInsets(
                WindowInsetsCompat.Type.navigationBars()
            ).bottom

            root.setPadding(
                originalRootPaddingLeft,
                originalRootPaddingTop,
                originalRootPaddingRight,
                originalRootPaddingBottom + navBottom
            )

            bottomSheet.post {
                dialog.behavior.peekHeight = bottomSheet.height
                dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
            }

            WindowInsetsCompat.CONSUMED
        }

        ViewCompat.requestApplyInsets(window.decorView)
    }

    private fun setupWindowForEdgeToEdge(window: Window) {
        WindowCompat.setDecorFitsSystemWindows(window, false)

        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        window.decorView.systemUiVisibility =
            window.decorView.systemUiVisibility or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }

    override fun onDestroyView() {
        rootView = null
        super.onDestroyView()
    }

    protected abstract fun onCreateBottomSheetView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View
}