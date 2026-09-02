package com.norwinlabs.tools

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.norwinlabs.tools.databinding.FragmentBudgetBinding
import com.norwinlabs.tools.databinding.ItemBudgetCategoryBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

class BudgetFragment : Fragment() {

    private var _binding: FragmentBudgetBinding? = null
    private val binding get() = _binding!!
    
    private val categories = mutableListOf(
        BudgetCategory("Rent/Mortgage", 0.0, 0xFFE91E63.toInt()),
        BudgetCategory("Food", 0.0, 0xFF2196F3.toInt()),
        BudgetCategory("Utilities", 0.0, 0xFFFFC107.toInt()),
        BudgetCategory("Entertainment", 0.0, 0xFF9C27B0.toInt())
    )
    
    private var monthlyIncome = 0.0
    private lateinit var allocationAdapter: AllocationAdapter
    private var saveJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBudgetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loadData()

        allocationAdapter = AllocationAdapter(categories) { updateCalculations() }
        binding.rvAllocations.layoutManager = LinearLayoutManager(context)
        binding.rvAllocations.adapter = allocationAdapter

        binding.editIncome.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                monthlyIncome = s.toString().toDoubleOrNull() ?: 0.0
                updateCalculations()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        binding.btnAddCategory.setOnClickListener {
            showAddCategoryDialog()
        }

        updateCalculations()
    }

    private fun updateCalculations() {
        var totalAllocated = 0.0
        categories.forEach { totalAllocated += it.amount }
        
        val extra = monthlyIncome - totalAllocated
        binding.tvExtraToInvest.text = String.format(Locale.US, "Extra to Invest: $%.2f", if (extra > 0) extra else 0.0)
        
        if (extra < 0) {
            binding.tvExtraToInvest.setTextColor(0xFFFF5252.toInt()) // Red for over-budget
        } else {
            binding.tvExtraToInvest.setTextColor(0xFF4CAF50.toInt()) // Green for healthy budget
        }

        binding.pieChart.setData(monthlyIncome, categories)
        scheduleSave()
    }

    /**
     * Every amount field recalculates on each keystroke, and saving used to be part of that -
     * so typing "1200" serialised the whole category list to JSON and wrote it to disk four
     * times. Coalesce those into one write once typing stops, and flush on the way out so
     * nothing is lost by leaving quickly.
     */
    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(SAVE_DEBOUNCE_MS)
            saveData()
        }
    }

    /**
     * apply() already hands the disk write to a background thread, so the only main-thread cost
     * here is building the JSON - negligible for a handful of categories once it is no longer
     * happening on every keystroke. Deliberately not a coroutine: this also runs from onPause,
     * where a cancelled scope could drop the final save.
     */
    private fun saveData() {
        val context = context?.applicationContext ?: return

        val jsonArray = JSONArray()
        categories.forEach { category ->
            val jsonObject = JSONObject()
            jsonObject.put("name", category.name)
            jsonObject.put("amount", category.amount)
            jsonObject.put("color", category.color)
            jsonArray.put(jsonObject)
        }

        context.getSharedPreferences("BudgetPrefs", Context.MODE_PRIVATE).edit()
            .putFloat("monthly_income", monthlyIncome.toFloat())
            .putString("categories_json", jsonArray.toString())
            .apply()
    }

    private fun loadData() {
        val prefs = requireContext().getSharedPreferences("BudgetPrefs", Context.MODE_PRIVATE)
        monthlyIncome = prefs.getFloat("monthly_income", 0.0f).toDouble()
        binding.editIncome.setText(if (monthlyIncome > 0) String.format(Locale.US, "%.2f", monthlyIncome) else "")
        
        val jsonString = prefs.getString("categories_json", null) ?: return
        // A malformed entry should cost one category, not take the screen down on open.
        val jsonArray = runCatching { JSONArray(jsonString) }.getOrNull() ?: return
        val restored = buildList {
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.optJSONObject(i) ?: continue
                add(
                    BudgetCategory(
                        jsonObject.optString("name", "Unnamed"),
                        jsonObject.optDouble("amount", 0.0),
                        jsonObject.optInt("color", 0xFF9E9E9E.toInt()),
                    )
                )
            }
        }
        if (restored.isNotEmpty()) {
            categories.clear()
            categories.addAll(restored)
        }
    }

    private fun showAddCategoryDialog() {
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 20, 50, 0)
        }

        val nameInput = EditText(context).apply { hint = "Category Name" }
        layout.addView(nameInput)

        AlertDialog.Builder(requireContext())
            .setTitle("New Budget Category")
            .setView(layout)
            .setPositiveButton("Add") { _, _ ->
                val name = nameInput.text.toString()
                if (name.isNotBlank()) {
                    val randomColor = (0xFF000000.toInt() until 0xFFFFFFFF.toInt()).random()
                    categories.add(BudgetCategory(name, 0.0, randomColor or 0xFF000000.toInt()))
                    allocationAdapter.notifyItemInserted(categories.size - 1)
                    updateCalculations()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    class AllocationAdapter(
        private val items: MutableList<BudgetCategory>,
        private val onAmountChanged: () -> Unit
    ) : RecyclerView.Adapter<AllocationAdapter.ViewHolder>() {

        class ViewHolder(val binding: ItemBudgetCategoryBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemBudgetCategoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.binding.tvCategoryName.text = item.name
            holder.binding.viewColorTag.setBackgroundColor(item.color)
            
            // Remove text watcher before setting text to avoid infinite loop
            holder.binding.editAmount.removeTextChangedListener(holder.binding.editAmount.tag as? TextWatcher)
            holder.binding.editAmount.setText(if (item.amount > 0) String.format(Locale.US, "%.2f", item.amount) else "")
            
            val watcher = object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    // bindingAdapterPosition returns NO_POSITION while the row is animating or
                    // detached; indexing with that crashed. Re-read the row rather than reusing
                    // the captured item, which may be stale after a reorder.
                    val position = holder.bindingAdapterPosition
                    if (position == RecyclerView.NO_POSITION) return
                    val newVal = s.toString().toDoubleOrNull() ?: 0.0
                    items[position] = items[position].copy(amount = newVal)
                    onAmountChanged()
                }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            }
            holder.binding.editAmount.addTextChangedListener(watcher)
            holder.binding.editAmount.tag = watcher
        }

        override fun getItemCount() = items.size
    }

    override fun onPause() {
        super.onPause()
        // Flush whatever the debounce is still holding before the screen goes away.
        saveJob?.cancel()
        saveJob = null
        saveData()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        saveJob?.cancel()
        saveJob = null
        _binding = null
    }

    private companion object {
        const val SAVE_DEBOUNCE_MS = 400L
    }
}