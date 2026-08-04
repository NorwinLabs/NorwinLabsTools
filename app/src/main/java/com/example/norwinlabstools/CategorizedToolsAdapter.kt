package com.example.norwinlabstools

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.norwinlabstools.databinding.ItemToolBinding

private sealed class ListEntry {
    data class Header(val category: String) : ListEntry()
    data class ToolEntry(val tool: Tool) : ListEntry()
}

/**
 * Groups tools by category with section headers, for the "Add Tool" sheet where users pick from
 * every tool at once. The Home screen's own grid stays a flat, user-ordered list (ToolsAdapter) -
 * grouping only makes sense for this "browse everything" surface.
 */
class CategorizedToolsAdapter(
    tools: List<Tool>,
    categoryOrder: List<String>,
    private val onToolClick: (Tool) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val entries: List<ListEntry> = tools
        .groupBy { it.category }
        .toList()
        .sortedBy { (category, _) -> categoryOrder.indexOf(category).let { if (it == -1) Int.MAX_VALUE else it } }
        .flatMap { (category, toolsInCategory) -> listOf(ListEntry.Header(category)) + toolsInCategory.map { ListEntry.ToolEntry(it) } }

    private val viewTypeHeader = 0
    private val viewTypeTool = 1

    class HeaderViewHolder(view: TextView) : RecyclerView.ViewHolder(view) {
        val textView = view
    }

    class ToolViewHolder(val binding: ItemToolBinding) : RecyclerView.ViewHolder(binding.root)

    override fun getItemViewType(position: Int): Int = when (entries[position]) {
        is ListEntry.Header -> viewTypeHeader
        is ListEntry.ToolEntry -> viewTypeTool
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == viewTypeHeader) {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_tool_category_header, parent, false) as TextView
            HeaderViewHolder(view)
        } else {
            val binding = ItemToolBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            ToolViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val entry = entries[position]) {
            is ListEntry.Header -> (holder as HeaderViewHolder).textView.text = entry.category
            is ListEntry.ToolEntry -> {
                val toolHolder = holder as ToolViewHolder
                bindToolCard(toolHolder.binding, entry.tool)
                toolHolder.binding.buttonRemove.visibility = android.view.View.GONE
                toolHolder.binding.cardTool.setOnClickListener { onToolClick(entry.tool) }
                toolHolder.binding.cardTool.setOnLongClickListener { false }
            }
        }
    }

    override fun getItemCount(): Int = entries.size

    /** Makes header rows span every column instead of taking one grid cell like a tool card. */
    fun spanSizeLookup(spanCount: Int): GridLayoutManager.SpanSizeLookup {
        return object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return if (entries[position] is ListEntry.Header) spanCount else 1
            }
        }
    }
}
