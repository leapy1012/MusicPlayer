package gd.app.musicplayer.ui.common

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.COUIRecyclerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Hosts a static content view as the single item of this [COUIRecyclerView] so it gets
 * COUI spring overscroll.
 *
 * ScrollView-based containers (including COUIScrollView / COUINestedScrollView) never
 * intercept a drag when their content fits the viewport, so short screens cannot
 * overscroll. A vertical RecyclerView always accepts the drag.
 *
 * [content] is detached from its current parent; view-binding references stay valid.
 */
fun COUIRecyclerView.hostStaticContent(content: View) {
    (content.parent as? ViewGroup)?.removeView(content)
    content.layoutParams = RecyclerView.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )
    content.visibility = View.VISIBLE

    layoutManager = LinearLayoutManager(context)
    itemAnimator = null
    overScrollMode = View.OVER_SCROLL_ALWAYS
    isNestedScrollingEnabled = true
    setOverScrollEnable(true)
    adapter = StaticContentAdapter(content)
}

private class StaticContentAdapter(
    private val content: View
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun getItemCount(): Int = 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        // The single content view can only have one holder; never let it be recycled.
        return object : RecyclerView.ViewHolder(content) {}.apply { setIsRecyclable(false) }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) = Unit
}
