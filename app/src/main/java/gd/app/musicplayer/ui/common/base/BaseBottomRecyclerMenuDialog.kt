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
import gd.app.musicplayer.core.ui.dialog.BaseBottomSheetDialogFragment
import gd.app.musicplayer.core.ui.drawable.DrawableUtil

abstract class BaseBottomRecyclerMenuDialog : BaseBottomSheetDialogFragment() {

    protected var recyclerView: RecyclerView? = null
        private set

    protected data class MenuItem(
        val id: Int,
        val iconResId: Int,
        val label: String? = null
    ) {
        companion object {
            fun create(id: Int, iconResId: Int): MenuItem {
                return MenuItem(id = id, iconResId = iconResId)
            }
        }

        fun withLabel(text: String): MenuItem = copy(label = text)
    }

    override fun onCreateBottomSheetView(
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
        (activity as? BaseActivity)?.applyThemeTo(view)
    }

    override fun onDestroyView() {
        recyclerView = null
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
