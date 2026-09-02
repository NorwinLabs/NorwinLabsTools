package com.norwinlabs.tools

import android.graphics.drawable.GradientDrawable
import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils

/**
 * Card artwork, drawn locally from each tool's own colour.
 *
 * Cards used to carry a remote Unsplash photo, so opening Home fired roughly twenty network
 * requests for decoration alone - on a cold or slow connection the grid appeared as flat colour
 * blocks and then popped as each photo landed. A gradient derived from the tool's seed colour
 * costs nothing, renders identically every time, and is legible offline.
 */
object ToolArtwork {

    /**
     * Only the colour ramp is cached, not the drawable. A Drawable instance shares its bounds
     * wherever it is set, so handing the same one to several visible cards makes them fight over
     * size; constructing a GradientDrawable per bind is cheap by comparison.
     */
    private val ramps = HashMap<Int, IntArray>()

    fun gradientFor(@ColorInt seed: Int): GradientDrawable {
        val colors = synchronized(ramps) { ramps.getOrPut(seed) { rampFor(seed) } }
        return GradientDrawable(GradientDrawable.Orientation.TL_BR, colors)
    }

    /**
     * Lifts the top-left and deepens the bottom-right around the seed. Saturation is nudged up
     * slightly at the light end so pale seeds keep their identity instead of washing to grey.
     */
    private fun rampFor(@ColorInt seed: Int): IntArray {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(seed, hsl)

        val light = hsl.copyOf().apply {
            this[1] = (this[1] * 1.10f).coerceAtMost(1f)
            this[2] = (this[2] + 0.16f).coerceAtMost(0.82f)
        }
        val dark = hsl.copyOf().apply {
            this[2] = (this[2] - 0.14f).coerceAtLeast(0.10f)
        }

        return intArrayOf(
            ColorUtils.HSLToColor(light),
            seed,
            ColorUtils.HSLToColor(dark),
        )
    }
}
