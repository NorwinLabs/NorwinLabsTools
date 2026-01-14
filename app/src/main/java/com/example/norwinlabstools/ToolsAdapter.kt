package com.example.norwinlabstools

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.RotateAnimation
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.example.norwinlabstools.databinding.ItemToolBinding
import java.util.Collections

class ToolsAdapter(
    private var tools: MutableList<Tool>,
    private val onToolClick: (Tool) -> Unit,
    private val onToolLongClick: (View, Tool) -> Unit,
    private val onRemoveClick: (Tool) -> Unit
) : RecyclerView.Adapter<ToolsAdapter.ToolViewHolder>() {

    var isEditMode: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                notifyItemRangeChanged(0, itemCount, "EDIT_MODE_CHANGE")
            }
        }

    fun getItems(): List<Tool> = tools

    fun updateTools(newTools: List<Tool>) {
        val diffCallback = object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = tools.size
            override fun getNewListSize(): Int = newTools.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
                tools[oldItemPosition].id == newTools[newItemPosition].id
            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
                tools[oldItemPosition] == newTools[newItemPosition]
        }
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        tools.clear()
        tools.addAll(newTools)
        diffResult.dispatchUpdatesTo(this)
    }

    class ToolViewHolder(private val binding: ItemToolBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(
            tool: Tool,
            isEditMode: Boolean,
            onToolClick: (Tool) -> Unit,
            onToolLongClick: (View, Tool) -> Unit,
            onRemoveClick: (Tool) -> Unit
        ) {
            binding.toolName.text = tool.name
            binding.toolIcon.setImageResource(tool.iconRes)
            binding.toolVersion.text = "v${tool.version}"
            
            // Apply overlay color
            binding.toolColorOverlay.setBackgroundColor(tool.color)
            
            // Load background image if available
            if (tool.imageUrl != null) {
                Glide.with(binding.root.context)
                    .load(tool.imageUrl)
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .placeholder(android.R.color.darker_gray)
                    .error(tool.color)
                    .into(binding.toolImageBackground)
            } else {
                binding.toolImageBackground.setImageDrawable(null)
                binding.toolBackground.setBackgroundColor(tool.color)
            }
            
            binding.buttonRemove.visibility = if (isEditMode) View.VISIBLE else View.GONE
            binding.buttonRemove.setOnClickListener { onRemoveClick(tool) }

            binding.cardTool.setOnClickListener { onToolClick(tool) }
            binding.cardTool.setOnLongClickListener {
                onToolLongClick(it, tool)
                true
            }

            if (isEditMode) {
                startBetterJiggleAnimation()
            } else {
                binding.root.clearAnimation()
            }
        }

        private fun startBetterJiggleAnimation() {
            val randomStart = (Math.random() * 100).toLong()
            val rotate = RotateAnimation(
                -1.5f, 1.5f,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f
            )
            rotate.duration = 120
            rotate.repeatCount = Animation.INFINITE
            rotate.repeatMode = Animation.REVERSE
            rotate.startOffset = randomStart
            binding.root.startAnimation(rotate)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ToolViewHolder {
        val binding = ItemToolBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ToolViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ToolViewHolder, position: Int) {
        holder.bind(tools[position], isEditMode, onToolClick, onToolLongClick, onRemoveClick)
    }

    override fun onBindViewHolder(holder: ToolViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains("EDIT_MODE_CHANGE")) {
            holder.bind(tools[position], isEditMode, onToolClick, onToolLongClick, onRemoveClick)
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    override fun getItemCount(): Int = tools.size

    fun removeTool(tool: Tool) {
        val index = tools.indexOf(tool)
        if (index != -1) {
            tools.removeAt(index)
            notifyItemRemoved(index)
        }
    }

    fun addTool(tool: Tool) {
        if (!tools.contains(tool)) {
            tools.add(tool)
            notifyItemInserted(tools.size - 1)
        }
    }

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
}