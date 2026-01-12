package com.example.norwinlabstools

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.example.norwinlabstools.databinding.FragmentFirstBinding

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class FirstFragment : Fragment() {

    private var _binding: FragmentFirstBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentFirstBinding.inflate(inflater, container, false)
        return binding.root

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tools = listOf(
            Tool(1, "Calculator", android.R.drawable.ic_menu_agenda),
            Tool(2, "Converter", android.R.drawable.ic_menu_compass),
            Tool(3, "Notes", android.R.drawable.ic_menu_edit),
            Tool(4, "Settings", android.R.drawable.ic_menu_manage),
            Tool(5, "About", android.R.drawable.ic_menu_info_details)
        )

        val adapter = ToolsAdapter(tools) { tool ->
            // For now, all tools navigate to SecondFragment
            findNavController().navigate(R.id.action_FirstFragment_to_SecondFragment)
        }

        binding.recyclerviewTools.layoutManager = GridLayoutManager(context, 2)
        binding.recyclerviewTools.adapter = adapter
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}