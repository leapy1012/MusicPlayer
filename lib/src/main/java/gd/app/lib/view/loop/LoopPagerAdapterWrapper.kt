package gd.app.lib.view.loop

import android.database.DataSetObserver
import android.os.Parcelable
import android.util.SparseArray
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentPagerAdapter
import androidx.fragment.app.FragmentStatePagerAdapter
import androidx.viewpager.widget.PagerAdapter

@Suppress("DEPRECATION")
class LoopPagerAdapterWrapper(
    private val realAdapter: PagerAdapter
) : PagerAdapter() {

    private var toDestroy = SparseArray<ToDestroy>()
    private var boundaryCaching = false
    private val dataObserver = object : DataSetObserver() {
        override fun onChanged() {
            notifyDataSetChanged()
        }

        override fun onInvalidated() {
            notifyDataSetChanged()
        }
    }

    init {
        realAdapter.registerDataSetObserver(dataObserver)
    }

    private val usesFragmentPaging: Boolean
        get() = realAdapter is FragmentPagerAdapter || realAdapter is FragmentStatePagerAdapter

    val realCount: Int
        get() = realAdapter.count

    private val hasLoopingBoundaries: Boolean
        get() = realCount > 1

    private val realFirstPosition: Int
        get() = 1

    private val realLastPosition: Int
        get() = realFirstPosition + realCount - 1

    fun setBoundaryCaching(enabled: Boolean) {
        boundaryCaching = enabled
    }

    fun getRealAdapter(): PagerAdapter = realAdapter

    fun release() {
        realAdapter.unregisterDataSetObserver(dataObserver)
    }

    override fun notifyDataSetChanged() {
        toDestroy = SparseArray()
        super.notifyDataSetChanged()
    }

    fun toRealPosition(position: Int): Int {
        val count = realCount
        if (count == 0) return 0
        if (!hasLoopingBoundaries) return position.coerceIn(0, count - 1)

        var realPosition = (position - 1) % count
        if (realPosition < 0) realPosition += count
        return realPosition
    }

    fun toInnerPosition(realPosition: Int): Int =
        if (hasLoopingBoundaries) realPosition + 1 else realPosition

    override fun getCount(): Int =
        if (hasLoopingBoundaries) realCount + 2 else realCount

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val realPosition = if (usesFragmentPaging && hasLoopingBoundaries) position else toRealPosition(position)

        if (boundaryCaching) {
            toDestroy[position]?.let { cached ->
                toDestroy.remove(position)
                return cached.obj
            }
        }

        return realAdapter.instantiateItem(container, realPosition)
    }

    override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
        val realPosition = if (usesFragmentPaging && hasLoopingBoundaries) position else toRealPosition(position)

        if (hasLoopingBoundaries && boundaryCaching && (position == realFirstPosition || position == realLastPosition)) {
            toDestroy.put(
                position,
                ToDestroy(
                    container = container,
                    position = realPosition,
                    obj = `object`
                )
            )
        } else {
            realAdapter.destroyItem(container, realPosition, `object`)
        }
    }

    override fun finishUpdate(container: ViewGroup) {
        realAdapter.finishUpdate(container)
    }

    override fun isViewFromObject(view: View, `object`: Any): Boolean {
        return realAdapter.isViewFromObject(view, `object`)
    }

    override fun restoreState(state: Parcelable?, loader: ClassLoader?) {
        realAdapter.restoreState(state, loader)
    }

    override fun saveState(): Parcelable? = realAdapter.saveState()

    override fun startUpdate(container: ViewGroup) {
        realAdapter.startUpdate(container)
    }

    override fun setPrimaryItem(container: ViewGroup, position: Int, `object`: Any) {
        realAdapter.setPrimaryItem(container, position, `object`)
    }

    private data class ToDestroy(
        val container: ViewGroup,
        val position: Int,
        val obj: Any
    )
}
