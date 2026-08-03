package com.example.norwinlabstools

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import com.example.norwinlabstools.databinding.FragmentConverterBinding
import java.util.Locale

class ConverterFragment : Fragment() {

    private var _binding: FragmentConverterBinding? = null
    private val binding get() = _binding!!

    private enum class Category(val label: String, val units: LinkedHashMap<String, Double>) {
        LENGTH("Length", linkedMapOf(
            "Meters" to 1.0,
            "Kilometers" to 1000.0,
            "Centimeters" to 0.01,
            "Miles" to 1609.34,
            "Yards" to 0.9144,
            "Feet" to 0.3048,
            "Inches" to 0.0254
        )),
        WEIGHT("Weight", linkedMapOf(
            "Kilograms" to 1.0,
            "Grams" to 0.001,
            "Pounds" to 0.453592,
            "Ounces" to 0.0283495
        )),
        TEMPERATURE("Temperature", linkedMapOf(
            "Celsius" to 0.0,
            "Fahrenheit" to 0.0,
            "Kelvin" to 0.0
        ))
    }

    private var selectedCategory = Category.LENGTH

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentConverterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.spinnerCategory.adapter = createAdapter(Category.entries.map { it.label })
        binding.spinnerCategory.onItemSelectedListener = selectListener { position ->
            selectedCategory = Category.entries[position]
            populateUnitSpinners()
        }

        binding.spinnerFromUnit.onItemSelectedListener = selectListener { convert() }
        binding.spinnerToUnit.onItemSelectedListener = selectListener { convert() }

        binding.btnSwap.setOnClickListener {
            val fromPos = binding.spinnerFromUnit.selectedItemPosition
            val toPos = binding.spinnerToUnit.selectedItemPosition
            binding.spinnerFromUnit.setSelection(toPos)
            binding.spinnerToUnit.setSelection(fromPos)
        }

        binding.editInputValue.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { convert() }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        populateUnitSpinners()
    }

    private fun createAdapter(items: List<String>): ArrayAdapter<String> {
        return ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, items).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
    }

    private fun selectListener(onSelected: (Int) -> Unit) = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = onSelected(position)
        override fun onNothingSelected(parent: AdapterView<*>?) {}
    }

    private fun populateUnitSpinners() {
        val units = selectedCategory.units.keys.toList()
        binding.spinnerFromUnit.adapter = createAdapter(units)
        binding.spinnerToUnit.adapter = createAdapter(units)
        if (units.size > 1) binding.spinnerToUnit.setSelection(1)
        convert()
    }

    private fun convert() {
        if (_binding == null) return
        val input = binding.editInputValue.text.toString().toDoubleOrNull()
        val fromUnit = binding.spinnerFromUnit.selectedItem as? String
        val toUnit = binding.spinnerToUnit.selectedItem as? String

        if (input == null || fromUnit == null || toUnit == null) {
            binding.tvResult.text = "Enter a value"
            return
        }

        val result = if (selectedCategory == Category.TEMPERATURE) {
            convertTemperature(input, fromUnit, toUnit)
        } else {
            val fromFactor = selectedCategory.units[fromUnit] ?: 1.0
            val toFactor = selectedCategory.units[toUnit] ?: 1.0
            input * fromFactor / toFactor
        }

        binding.tvResult.text = String.format(Locale.US, "%.4f %s = %.4f %s", input, fromUnit, result, toUnit)
    }

    private fun convertTemperature(value: Double, from: String, to: String): Double {
        val celsius = when (from) {
            "Celsius" -> value
            "Fahrenheit" -> (value - 32) * 5.0 / 9.0
            "Kelvin" -> value - 273.15
            else -> value
        }
        return when (to) {
            "Celsius" -> celsius
            "Fahrenheit" -> celsius * 9.0 / 5.0 + 32
            "Kelvin" -> celsius + 273.15
            else -> celsius
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
