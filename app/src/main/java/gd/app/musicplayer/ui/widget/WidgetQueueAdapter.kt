package gd.app.musicplayer.ui.widget

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import gd.app.musicplayer.R

internal class WidgetQueueAdapter(
    private val context: Context,
    private var primaryColor: Int,
    private var secondaryColor: Int
) : BaseAdapter() {

    private val items = listOf(
        "Shape of You" to "Ed Sheeran",
        "See You Again" to "Wiz Khalifa & Charlie Puth",
        "Uptown Funk" to "Bruno Mars",
        "Thinking Out Loud" to "Ed Sheeran",
        "Sorry" to "Justin Bieber",
        "Sugar" to "Maroon 5"
    )

    override fun getCount(): Int = items.size

    override fun getItem(position: Int): Pair<String, String> = items[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(
        position: Int,
        convertView: View?,
        parent: ViewGroup
    ): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(
            R.layout.widget_queue_item,
            parent,
            false
        )
        val holder = (view.tag as? ViewHolder) ?: ViewHolder(view).also { view.tag = it }
        val item = items[position]

        holder.position.text = (position + 1).toString()
        holder.title.text = item.first
        holder.artist.text = item.second
        holder.position.setTextColor(secondaryColor)
        holder.title.setTextColor(primaryColor)
        holder.artist.setTextColor(secondaryColor)
        holder.divider.visibility = View.GONE
        return view
    }

    fun updateColors(
        primaryColor: Int,
        secondaryColor: Int
    ) {
        if (this.primaryColor == primaryColor && this.secondaryColor == secondaryColor) {
            return
        }
        this.primaryColor = primaryColor
        this.secondaryColor = secondaryColor
        notifyDataSetChanged()
    }

    private class ViewHolder(root: View) {
        val position: TextView = root.findViewById(R.id.widget_queue_item_position)
        val title: TextView = root.findViewById(R.id.widget_queue_item_title)
        val artist: TextView = root.findViewById(R.id.widget_queue_item_artist)
        val divider: View = root.findViewById(R.id.widget_queue_item_divider)
    }
}
