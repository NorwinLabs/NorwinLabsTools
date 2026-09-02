package com.norwinlabs.tools

import com.norwinlabs.tools.databinding.ItemToolBinding

/** Shared rendering for a tool card (gradient background + icon + name + version). */
fun bindToolCard(binding: ItemToolBinding, tool: Tool) {
    binding.toolName.text = tool.name
    binding.toolIcon.setImageResource(tool.iconRes)
    binding.toolVersion.text = "v${tool.version}"

    // The card's text and icon are always white, so the background has to carry the contrast on
    // its own. The gradient is opaque and drawn from the tool's colour - see ToolArtwork.
    binding.toolBackgroundArt.background = ToolArtwork.gradientFor(tool.color)
}
