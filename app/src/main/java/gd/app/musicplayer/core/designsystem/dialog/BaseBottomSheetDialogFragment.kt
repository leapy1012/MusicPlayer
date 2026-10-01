package gd.app.musicplayer.core.designsystem.dialog

import android.content.DialogInterface
import android.app.Dialog
import android.graphics.Color
import android.graphics.Outline
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.Window
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.coui.appcompat.panel.COUIBottomSheetDialog
import com.coui.appcompat.panel.COUIPanelPercentFrameLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import gd.app.musicplayer.R
import gd.app.musicplayer.core.designsystem.theme.ThemeObserver
import gd.app.musicplayer.core.designsystem.theme.ThemePalette
import gd.app.musicplayer.core.designsystem.theme.ThemeRegistry
import gd.app.musicplayer.ui.theme.ThemeEngine
import javax.inject.Inject

abstract class BaseBottomSheetDialogFragment : BottomSheetDialogFragment(), ThemeObserver {

    @Inject
    lateinit var themeEngine: ThemeEngine

    @Inject
    lateinit var themeRegistry: ThemeRegistry

    protected var rootView: View? = null
        private set

    private var originalRootPaddingLeft = 0
    private var originalRootPaddingTop = 0
    private var originalRootPaddingRight = 0
    private var originalRootPaddingBottom = 0
    private var sheetBottomMarginPx = 0

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return COUIBottomSheetDialog(
            requireContext(),
            R.style.App_COUI_BottomSheetDialog
        ).apply {
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
        return R.style.App_COUI_BottomSheetDialog
    }

    override fun onStart() {
        super.onStart()
        themeRegistry.registerObserver(this)
        rootView?.let { root ->
            applyBottomSheetSurface(root)
            applyThemeTo(root)
            (dialog as? BottomSheetDialog)
                ?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
                ?.let { sheet -> squareOffSheetCorners(sheet, root) }
        }

        val dialog = dialog as? BottomSheetDialog ?: return
        val window = dialog.window ?: return

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        window.statusBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        applyNavigationChrome(
            dialog = dialog,
            window = window,
            bottomSheet = dialog.findViewById(
                com.google.android.material.R.id.design_bottom_sheet
            ),
            root = rootView,
            insets = ViewCompat.getRootWindowInsets(window.decorView),
        )
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

        // Always edge-to-edge so the sheet covers the activity mini-player.
        window.decorView.fitsSystemWindows = false
        container?.fitsSystemWindows = false
        coordinator?.fitsSystemWindows = false
        bottomSheet.fitsSystemWindows = false
        root.fitsSystemWindows = false

        applyBottomSheetSurface(root)
        squareOffSheetCorners(bottomSheet, root)

        dialog.behavior.isGestureInsetBottomIgnored = true
        dialog.behavior.skipCollapsed = true
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        applyFixedPanelHeightIfNeeded(dialog, bottomSheet)

        (dialog as? COUIBottomSheetDialog)?.setCouiPanelEdgeToEdgeEnable(true)

        bottomSheet.post {
            squareOffSheetCorners(bottomSheet, root)
            applyFixedPanelHeightIfNeeded(dialog, bottomSheet)
            refreshCouiNavigationStrip(dialog, root)
            dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }

        // Do not consume insets — COUI needs them for nav custom-view height / panel margin.
        ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { _, insets ->
            applyNavigationChrome(dialog, window, bottomSheet, root, insets)

            bottomSheet.post {
                squareOffSheetCorners(bottomSheet, root)
                applyFixedPanelHeightIfNeeded(dialog, bottomSheet)
                refreshCouiNavigationStrip(dialog, root)
                dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
            }

            insets
        }

        ViewCompat.requestApplyInsets(window.decorView)
    }

    /**
     * COUI edge-to-edge: transparent system nav + plate-colored nav strip behind the
     * 3-button icons. Pad Close by one nav inset so it clears the buttons; that pad is
     * the same plate (not a dead gap above a separate bar).
     */
    private fun applyNavigationChrome(
        dialog: BottomSheetDialog,
        window: Window,
        bottomSheet: View?,
        root: View?,
        insets: WindowInsetsCompat?,
    ) {
        val lightPlate = themeEngine.currentTheme().isDialogSurfaceLight()
        val navBottom = insets?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0

        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        (dialog as? COUIBottomSheetDialog)?.setCouiPanelEdgeToEdgeEnable(true)

        // Transparent system nav so the sheet / COUI strip shows behind the icons.
        window.navigationBarColor = Color.TRANSPARENT
        root?.setPadding(
            originalRootPaddingLeft,
            originalRootPaddingTop,
            originalRootPaddingRight,
            originalRootPaddingBottom + navBottom,
        )
        setBottomSheetBottomMargin(bottomSheet, 0)
        refreshCouiNavigationStrip(dialog, root)

        applyNavigationBarIconContrast(window, lightPlate)
        window.decorView.post { applyNavigationBarIconContrast(window, lightPlate) }
        window.decorView.postDelayed({ applyNavigationBarIconContrast(window, lightPlate) }, 50L)
    }

    private fun applyNavigationBarIconContrast(window: Window, lightPlate: Boolean) {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightNavigationBars = lightPlate
            isAppearanceLightStatusBars = lightPlate
        }
        @Suppress("DEPRECATION")
        val decor = window.decorView
        val flags = decor.systemUiVisibility
        decor.systemUiVisibility = if (lightPlate) {
            flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        } else {
            flags and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv() and
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
        }
    }

    /**
     * Ensure COUI's bottom nav strip uses the same plate as the sheet (pictured wash included)
     * so the panel appears continuous behind the system buttons.
     *
     * [COUIBottomSheetDialog.initOrRefreshNavigationView] reads `mApplyWindowInsets` and
     * NPEs when called before the first inset dispatch — only invoke once insets exist.
     */
    private fun refreshCouiNavigationStrip(dialog: BottomSheetDialog, contentRoot: View?) {
        val coui = dialog as? COUIBottomSheetDialog ?: return
        val insetsReady = dialog.window?.decorView?.rootWindowInsets != null
        if (insetsReady) {
            runCatching { coui.initOrRefreshNavigationView() }
        }
        val plate = contentRoot?.let { root ->
            themeEngine.currentTheme().getBottomDialogSurfaceDrawable(root.context)
        } ?: return
        val container = dialog.findViewById<View>(com.coui.appcompat.R.id.container) ?: return
        val parent = container.parent as? ViewGroup ?: return
        for (index in 0 until parent.childCount) {
            val child = parent.getChildAt(index) ?: continue
            if (child === container || child is ViewGroup) continue
            val params = child.layoutParams as? FrameLayout.LayoutParams ?: continue
            if (params.gravity and android.view.Gravity.BOTTOM != android.view.Gravity.BOTTOM) continue
            child.background = plate.constantState?.newDrawable()?.mutate() ?: plate
            child.visibility = View.VISIBLE
        }
    }

    private fun setBottomSheetBottomMargin(bottomSheet: View?, marginPx: Int) {
        sheetBottomMarginPx = marginPx
        val params = bottomSheet?.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        if (params.bottomMargin == marginPx) return
        params.bottomMargin = marginPx
        bottomSheet.layoutParams = params
    }

    /**
     * Optional fixed panel height in px (e.g. queue sheet = 60%/72% of screen).
     * When null, COUI keeps its default content-sized panel.
     */
    protected open fun fixedPanelHeightPx(): Int? = null

    /**
     * Drive height through [COUIBottomSheetDialog.setHeight] so COUI's
     * content-height / spring path does not collapse the panel.
     *
     * Keep [BottomSheetBehavior.isFitToContents] true: with it false the
     * sheet expands to the parent and the fixed-height content pins to the
     * top (queue chrome above the wallpaper, drag bar mid-screen).
     */
    private fun applyFixedPanelHeightIfNeeded(
        dialog: BottomSheetDialog,
        bottomSheet: View,
    ) {
        val fixed = fixedPanelHeightPx()?.takeIf { it > 0 }
        if (fixed == null) {
            dialog.behavior.peekHeight = bottomSheet.height
            return
        }

        (dialog as? COUIBottomSheetDialog)?.let { coui ->
            coui.setHeightChangeAnim(false)
            coui.setHeight(fixed)
        }
        dialog.behavior.isFitToContents = true
        dialog.behavior.skipCollapsed = true
        dialog.behavior.peekHeight = fixed
        // WRAP_CONTENT on design_bottom_sheet so the behavior anchors the
        // fixed-height COUI content layout to the bottom of the window.
        val params = bottomSheet.layoutParams
        var changed = false
        if (params.height != ViewGroup.LayoutParams.WRAP_CONTENT) {
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT
            changed = true
        }
        (params as? ViewGroup.MarginLayoutParams)?.let { marginParams ->
            if (marginParams.bottomMargin != sheetBottomMarginPx) {
                marginParams.bottomMargin = sheetBottomMarginPx
                changed = true
            }
        }
        if (changed) {
            bottomSheet.layoutParams = params
        }
    }

    /**
     * Picture: blurred theme image + teal wash ([ThemePalette.getBottomDialogSurfaceDrawable]).
     * Light/Dark: solid COUI/dark surfaces from the same API.
     */
    private fun applyBottomSheetSurface(root: View) {
        val palette = themeEngine.currentTheme()
        // Pictured-only frosted bg; light/dark keep palette solids (not hardcoded black).
        root.background = palette.getBottomDialogSurfaceDrawable(root.context)
    }

    /**
     * Force a square plate from [contentRoot] up through [bottomSheet].
     *
     * COUI panels round via both drawable corners (`?couiRoundCornerXL`) and
     * [COUIPanelPercentFrameLayout]'s path clip. Clearing `clipToOutline` makes
     * that frame fall back to its smooth-round path — use a rectangular outline
     * with clipping instead so `draw()` takes the non-path branch.
     */
    private fun squareOffSheetCorners(bottomSheet: View, contentRoot: View) {
        val plate = themeEngine.currentTheme().getBottomDialogSurfaceDrawable(contentRoot.context)
        (dialog as? COUIBottomSheetDialog)?.let { couiDialog ->
            couiDialog.setPanelBackground(
                plate.constantState?.newDrawable()?.mutate() ?: plate
            )
            couiDialog.setUseNormalSmoothCorner(true)
        }
        var node: View? = contentRoot
        while (node != null) {
            flattenPanelCorners(node, plate)
            if (node === bottomSheet) break
            node = node.parent as? View
        }
        // Walk past design_bottom_sheet in case the percent frame sits above it.
        var above: View? = bottomSheet.parent as? View
        var depth = 0
        while (above != null && depth < 4) {
            if (above is COUIPanelPercentFrameLayout) {
                flattenPanelCorners(above, plate)
            }
            above = above.parent as? View
            depth++
        }
        if (bottomSheet is ViewGroup) {
            for (index in 0 until bottomSheet.childCount) {
                val child = bottomSheet.getChildAt(index)
                if (child === contentRoot || isAncestorOf(child, contentRoot)) {
                    flattenPanelCorners(child, plate, replaceBackground = child !== contentRoot)
                }
            }
        }
    }

    private fun flattenPanelCorners(
        view: View,
        plate: android.graphics.drawable.Drawable,
        replaceBackground: Boolean = true,
    ) {
        if (replaceBackground) {
            view.background = plate.constantState?.newDrawable()?.mutate() ?: plate
        }
        // Square outline + clip: COUIPanelPercentFrameLayout skips path rounding when
        // clipToOutline is true (see its draw() branch).
        view.outlineProvider = SQUARE_OUTLINE
        view.clipToOutline = true
        if (view is COUIPanelPercentFrameLayout) {
            view.setUseNormalSmoothCorner(true)
            zeroPanelRadius(view)
        }
    }

    /** Best-effort: zero private mRadius so any path fallback stays square. */
    private fun zeroPanelRadius(panel: COUIPanelPercentFrameLayout) {
        try {
            val field = COUIPanelPercentFrameLayout::class.java.getDeclaredField("mRadius")
            field.isAccessible = true
            field.setFloat(panel, 0f)
            panel.invalidate()
        } catch (_: Throwable) {
            // API may change; rectangular clipToOutline is the primary fix.
        }
    }

    private fun isAncestorOf(ancestor: View, descendant: View): Boolean {
        var node: View? = descendant
        while (node != null) {
            if (node === ancestor) return true
            node = node.parent as? View
        }
        return false
    }

    private fun setupWindowForEdgeToEdge(window: Window) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
    }

    override fun onDestroyView() {
        rootView = null
        super.onDestroyView()
    }

    override fun onDismiss(dialog: DialogInterface) {
        clearPendingShow(tag)
        super.onDismiss(dialog)
    }

    override fun show(manager: FragmentManager, tag: String?) {
        val key = tag ?: javaClass.name
        if (!markPendingShow(manager, key)) return
        super.show(manager, tag)
    }

    override fun show(transaction: FragmentTransaction, tag: String?): Int {
        val key = tag ?: javaClass.name
        if (!markPendingShow(key)) return -1
        return super.show(transaction, tag)
    }

    override fun onStop() {
        themeRegistry.unregisterObserver(this)
        super.onStop()
    }

    override fun onThemeChanged(palette: ThemePalette?) {
        rootView?.let { root ->
            applyBottomSheetSurface(root)
            applyThemeTo(root)
            (dialog as? BottomSheetDialog)?.let { bottomSheetDialog ->
                val sheet = bottomSheetDialog.findViewById<View>(
                    com.google.android.material.R.id.design_bottom_sheet
                )
                sheet?.let { squareOffSheetCorners(it, root) }
                bottomSheetDialog.window?.let { window ->
                    applyNavigationChrome(
                        dialog = bottomSheetDialog,
                        window = window,
                        bottomSheet = sheet,
                        root = root,
                        insets = ViewCompat.getRootWindowInsets(window.decorView),
                    )
                }
            }
        }
    }

    protected fun applyThemeTo(root: View?) {
        themeEngine.apply(root)
    }

    protected abstract fun onCreateBottomSheetView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View

    private companion object {
        private val SQUARE_OUTLINE = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRect(0, 0, view.width, view.height)
            }
        }

        private const val PENDING_SHOW_TIMEOUT_MS = 1000L
        private val pendingShows = mutableMapOf<String, Long>()

        @Synchronized
        private fun markPendingShow(manager: FragmentManager, key: String): Boolean {
            val now = System.currentTimeMillis()
            pendingShows.entries.removeAll { now - it.value > PENDING_SHOW_TIMEOUT_MS }

            if (manager.isStateSaved || manager.findFragmentByTag(key) != null) return false
            if (pendingShows.containsKey(key)) return false

            pendingShows[key] = now
            return true
        }

        @Synchronized
        private fun markPendingShow(key: String): Boolean {
            val now = System.currentTimeMillis()
            pendingShows.entries.removeAll { now - it.value > PENDING_SHOW_TIMEOUT_MS }

            if (pendingShows.containsKey(key)) return false

            pendingShows[key] = now
            return true
        }

        @Synchronized
        private fun clearPendingShow(key: String?) {
            if (key != null) {
                pendingShows.remove(key)
            }
        }
    }
}
