package com.example.norwinlabstools

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.norwinlabstools.databinding.ItemToolBinding

class ToolsAdapter(
    private val tools: List<Tool>,
    private val onToolClick: (Tool) -> Unit
) : RecyclerView.Adapter<ToolsAdapter.ToolViewHolder>() {

    class ToolViewHolder(private val binding: ItemToolBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(tool: Tool, onToolClick: (Tool) -> Unit) {
            binding.toolName.text = tool.name
            binding.toolIcon.setImageResource(tool.iconRes)
            binding.root.setOnClickListener { onToolClick(tool) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ToolViewHolder {
        val binding = ItemToolBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ToolViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ToolViewHolder, position: Int) {
        holder.bind(tools[position], onToolClick)
    }

    override fun getItemCount(): Int = tools.size
}