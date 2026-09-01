package com.norwinlabs.tools

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.norwinlabs.tools.databinding.ItemToolBinding
import java.util.Collections

class ToolsAdapter(
    private var tools: MutableList<Tool>,
    private val onToolClick: (Tool) -> Unit,
    private val onToolLongClick: (View, Tool) -> Unit,
    private val onRemoveClick: (Tool) -> Unit
) : RecyclerView.Adapter<ToolsAdapter.ToolViewHolder>() {

    var isEditMode = false
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    class ToolViewHolder(val binding: ItemToolBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(tool: Tool, onToolClick: (Tool) -> Unit, onToolLongClick: (View, Tool) -> Unit, onRemoveClick: (Tool) -> Unit, isEditMode: Boolean) {
            bindToolCard(binding, tool)

            binding.buttonRemove.visibility = if (isEditMode) View.VISIBLE else View.GONE
            binding.buttonRemove.setOnClickListener { onRemoveClick(tool) }

            binding.cardTool.setOnClickListener { onToolClick(tool) }
            binding.cardTool.setOnLongClickListener {
                onToolLongClick(binding.cardTool, tool)
                true
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ToolViewHolder {
        val binding = ItemToolBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ToolViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ToolViewHolder, position: Int) {
        holder.bind(tools[position], onToolClick, onToolLongClick, onRemoveClick, isEditMode)
    }

    override fun getItemCount(): Int = tools.size

    fun onItemMove(fromPosition: Int, toPosition: Int) {
        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                Collections.swap(tools, i, i + 1)
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                Collections.swap(tools, i, i - 1)
            }
        }
        notifyItemMoved(fromPosition, toPosition)
    }

    /** Replaces the whole list, for the initial load once the saved layout has been read. */
    fun setTools(newTools: List<Tool>) {
        tools.clear()
        tools.addAll(newTools)
        notifyDataSetChanged()
    }

    fun addTool(tool: Tool) {
        tools.add(tool)
        notifyItemInserted(tools.size - 1)
    }

    fun removeTool(tool: Tool) {
        val position = tools.indexOf(tool)
        if (position != -1) {
            tools.removeAt(position)
            notifyItemRemoved(position)
        }
    }

    fun getItems(): List<Tool> = tools
}