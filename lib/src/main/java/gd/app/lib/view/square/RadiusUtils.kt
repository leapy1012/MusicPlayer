package gd.app.lib.view.square

import android.graphics.Outline
import android.view.View
import android.view.ViewOutlineProvider
import java.util.Arrays
import kotlin.math.abs

object RadiusUtils {

    private const val FLOAT_EPSILON = 1.0E-4f

    fun all(radius: Float): FloatArray {
        return FloatArray(8).also { radii ->
            Arrays.fill(radii, radius.coerceAtLeast(0f))
        }
    }

    fun normalize(radii: FloatArray): FloatArray {
        return when (radii.size) {
            8 -> radii.copyOf()

            4 -> FloatArray(8).also { result ->
                for (index in 0 until 4) {
                    val radius = radii[index].coerceAtLeast(0f)
                    val targetIndex = index * 2
                    result[targetIndex] = radius
                    result[targetIndex + 1] = radius
                }
            }

            1 -> all(radii[0])

            else -> throw IllegalArgumentException(
                "The length of radii must be 1, 4 or 8."
            )
        }
    }

    fun areEqual(first: Float, second: Float): Boolean {
        return abs(first - second) < FLOAT_EPSILON
    }

    fun clipToRoundedOutline(view: View, radius: Float) {
        view.clipToOutline = true
        view.outlineProvider = RoundedOutlineProvider(radius)
    }

    private class RoundedOutlineProvider(
        private val radius: Float
    ) : ViewOutlineProvider() {

        override fun getOutline(view: View, outline: Outline) {
            outline.setRoundRect(
                0,
                0,
                view.width,
                view.height,
                radius
            )
        }
    }
}
