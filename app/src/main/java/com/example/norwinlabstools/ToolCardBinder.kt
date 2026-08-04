package com.example.norwinlabstools

import android.view.View
import com.bumptech.glide.Glide
import com.example.norwinlabstools.databinding.ItemToolBinding

/** Shared rendering for a tool card (image/color background + icon + name + version). */
fun bindToolCard(binding: ItemToolBinding, tool: Tool) {
    binding.toolName.text = tool.name
    binding.toolIcon.setImageResource(tool.iconRes)
    binding.toolVersion.text = "v${tool.version}"

    // The card's text/icon is always white, so it needs contrast from something behind it.
    // When there's a background photo, tool.color is a subtle tint over it; when there isn't,
    // it needs to be a fully opaque background on its own.
    binding.toolColorOverlay.setBackgroundColor(tool.color)
    binding.toolColorOverlay.visibility = View.VISIBLE

    if (tool.imageUrl != null) {
        Glide.with(binding.root.context)
            .load(tool.imageUrl)
            .centerCrop()
            .into(binding.toolImageBackground)
        binding.toolImageBackground.visibility = View.VISIBLE
        binding.toolColorOverlay.alpha = 0.4f
    } else {
        binding.toolImageBackground.visibility = View.GONE
        binding.toolColorOverlay.alpha = 1f
    }
}
