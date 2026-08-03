package com.example.norwinlabstools

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.norwinlabstools.databinding.FragmentUe5GuideBinding

class UE5GuideFragment : Fragment() {

    private var _binding: FragmentUe5GuideBinding? = null
    private val binding get() = _binding!!

    data class Tip(val category: String, val title: String, val detail: String)

    private val tips = listOf(
        Tip("Performance", "Profile with Unreal Insights", "Use Unreal Insights (stat startfile / stat stopfile) instead of guessing at frame spikes; it captures game thread, render thread, and GPU timing in one trace."),
        Tip("Performance", "Nanite has real costs", "Nanite removes per-triangle draw call overhead but still costs during the visibility/rasterization pass on very high overdraw scenes - check on the Nanite view mode before assuming it's free."),
        Tip("Lighting", "Lumen needs sensible scale", "Lumen's quality and cost scale with scene scale; oversized meshes (bad unit scale on import) make Lumen noisy and expensive. Keep 1 Unreal unit = 1 cm."),
        Tip("Blueprints", "Avoid Tick when you can", "Prefer timers, event dispatchers, or animation notifies over Tick for anything that doesn't need per-frame logic - it adds up fast across many actors."),
        Tip("Blueprints", "Use Blueprint interfaces for decoupling", "Communicate between unrelated actors (e.g. a trap and the player) via Blueprint Interfaces instead of casting directly, so systems don't need to know each other's concrete class."),
        Tip("Packaging", "Check your cook logs", "A slow or oversized cook is almost always visible in the Saved/Logs cook log - look for redundant duplicated textures or uncompressed audio before blaming the engine."),
        Tip("Packaging", "Strip debug symbols for shipping", "Shipping configuration builds should have full symbol stripping and no editor-only content packaged; use the platform-specific packaging settings to confirm this before a release build."),
        Tip("Accessibility", "Rebindable controls first", "Build input around Enhanced Input's Input Action assets from day one - retrofitting rebindable controls onto hardcoded input bindings later is much more painful."),
        Tip("Accessibility", "Don't rely on color alone", "Pair any color-coded UI or gameplay state (health, factions, hazards) with a shape, icon, or pattern so colorblind players aren't at a disadvantage."),
        Tip("Version Control", "Lock binary assets", "Uassets and other binary files don't merge - use your VCS's file locking (e.g. Perforce checkout, or Git LFS locks) so two people don't silently clobber each other's level edits.")
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUe5GuideBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvUe5Tips.layoutManager = LinearLayoutManager(context)
        binding.rvUe5Tips.adapter = TipAdapter(tips)
    }

    class TipAdapter(private val items: List<Tip>) : RecyclerView.Adapter<TipAdapter.ViewHolder>() {
        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvTitle: android.widget.TextView = view.findViewById(android.R.id.text1)
            val tvDetail: android.widget.TextView = view.findViewById(android.R.id.text2)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_2, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val tip = items[position]
            holder.tvTitle.text = "[${tip.category}] ${tip.title}"
            holder.tvTitle.setTextColor(0xFF00695C.toInt())
            holder.tvDetail.text = tip.detail
        }

        override fun getItemCount() = items.size
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
