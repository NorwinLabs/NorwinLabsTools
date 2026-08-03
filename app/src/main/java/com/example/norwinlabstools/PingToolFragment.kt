package com.example.norwinlabstools

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.norwinlabstools.databinding.FragmentPingToolBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

class PingToolFragment : Fragment() {

    private var _binding: FragmentPingToolBinding? = null
    private val binding get() = _binding!!

    // Hostnames/IPv4/IPv6 only - rejects anything that could be misread as an extra ping flag.
    private val hostPattern = Regex("^[A-Za-z0-9.\\-:]+$")

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPingToolBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnPing.setOnClickListener {
            val host = binding.editHost.text.toString().trim()
            when {
                host.isBlank() -> Toast.makeText(context, "Please enter a host", Toast.LENGTH_SHORT).show()
                !hostPattern.matches(host) -> Toast.makeText(context, "Invalid host", Toast.LENGTH_SHORT).show()
                else -> runPing(host)
            }
        }
    }

    private fun runPing(host: String) {
        binding.tvResults.text = "Pinging $host…"
        binding.progressBar.visibility = View.VISIBLE
        binding.btnPing.isEnabled = false

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val process = ProcessBuilder("/system/bin/ping", "-c", "4", "-W", "2", host)
                        .redirectErrorStream(true)
                        .start()
                    val text = BufferedReader(InputStreamReader(process.inputStream)).readText()
                    process.waitFor()
                    text.ifBlank { "No response from $host" }
                } catch (e: Exception) {
                    "Ping failed: ${e.message}"
                }
            }

            if (_binding == null) return@launch
            binding.progressBar.visibility = View.GONE
            binding.btnPing.isEnabled = true
            binding.tvResults.text = result
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
