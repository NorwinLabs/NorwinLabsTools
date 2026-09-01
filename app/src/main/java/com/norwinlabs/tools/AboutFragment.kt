package com.norwinlabs.tools

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.norwinlabs.tools.databinding.FragmentAboutBinding

class AboutFragment : Fragment() {

    private var _binding: FragmentAboutBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAboutBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvVersion.text = "Version ${getVersionInfo()}"

        binding.btnWindhelmSite.setOnClickListener { openUrl("https://windhelm.dev") }
        binding.btnGithub.setOnClickListener { openUrl("https://github.com/NorwinLabs/NorwinLabsTools") }
        binding.btnTrello.setOnClickListener { openUrl("https://trello.com/b/SVY6LFSZ/windhelm-main-development") }
    }

    @Suppress("DEPRECATION")
    private fun getVersionInfo(): String {
        return try {
            val packageInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
            "${packageInfo.versionName} (build ${packageInfo.versionCode})"
        } catch (e: PackageManager.NameNotFoundException) {
            "unknown"
        }
    }

    private fun openUrl(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
