package com.norwinlabs.tools

import android.view.View
import com.norwinlabs.tools.databinding.ItemToolBinding

/** Corner radius of the coloured icon badge, in dp. */
private const val BADGE_RADIUS_DP = 16f

/**
 * Shared rendering for a tool tile: a coloured icon badge, the name, and optionally the category.
 *
 * @param showCategory Home mixes categories in one grid so the caption is useful there; the
 * Tools browser already groups under category headers, where repeating it would be noise.
 */
fun bindToolCard(binding: ItemToolBinding, tool: Tool, showCategory: Boolean = true) {
    binding.toolName.text = tool.name
    binding.toolIcon.setImageResource(tool.iconRes)
    binding.toolVersion.text = tool.category
    binding.toolVersion.visibility = if (showCategory) View.VISIBLE else View.GONE

    // The icon is always white, so the badge carries the contrast on its own. The gradient is
    // opaque and drawn from the tool's colour - see ToolArtwork.
    val density = binding.root.resources.displayMetrics.density
    binding.toolBackgroundArt.background = ToolArtwork.gradientFor(tool.color).apply {
        cornerRadius = BADGE_RADIUS_DP * density
    }
}
