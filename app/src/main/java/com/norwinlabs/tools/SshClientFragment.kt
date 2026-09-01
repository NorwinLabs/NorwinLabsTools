package com.norwinlabs.tools

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.norwinlabs.tools.databinding.FragmentSshClientBinding
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class SshClientFragment : Fragment() {

    private var _binding: FragmentSshClientBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSshClientBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnRun.setOnClickListener {
            val host = binding.editHost.text.toString().trim()
            val port = binding.editPort.text.toString().toIntOrNull() ?: 22
            val user = binding.editUsername.text.toString().trim()
            val password = binding.editPassword.text.toString()
            val command = binding.editCommand.text.toString().trim()

            when {
                host.isBlank() -> toast("Enter a host")
                user.isBlank() -> toast("Enter a username")
                command.isBlank() -> toast("Enter a command to run")
                else -> runSshCommand(host, port, user, password, command)
            }
        }
    }

    private fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    private fun runSshCommand(host: String, port: Int, user: String, password: String, command: String) {
        binding.tvOutput.text = "Connecting to $user@$host:$port…"
        binding.progressBar.visibility = View.VISIBLE
        binding.btnRun.isEnabled = false

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                var session: Session? = null
                var channel: ChannelExec? = null
                try {
                    val jsch = JSch()
                    session = jsch.getSession(user, host, port)
                    session.setPassword(password)
                    // Quick-connect utility, not a hardened enterprise client: skip host key
                    // verification rather than requiring the user to manage a known_hosts file.
                    session.setConfig("StrictHostKeyChecking", "no")
                    session.connect(10000)

                    channel = session.openChannel("exec") as ChannelExec
                    channel.setCommand(command)
                    val outputStream = ByteArrayOutputStream()
                    val errorStream = ByteArrayOutputStream()
                    channel.setOutputStream(outputStream)
                    channel.setErrStream(errorStream)
                    channel.connect(10000)

                    while (!channel.isClosed) {
                        Thread.sleep(50)
                    }

                    val exitStatus = channel.exitStatus
                    buildString {
                        append(outputStream.toString("UTF-8"))
                        if (errorStream.size() > 0) {
                            append("\n--- stderr ---\n")
                            append(errorStream.toString("UTF-8"))
                        }
                        append("\n(exit code $exitStatus)")
                    }
                } catch (e: Exception) {
                    "Connection failed: ${e.message}"
                } finally {
                    channel?.disconnect()
                    session?.disconnect()
                }
            }

            if (_binding == null) return@launch
            binding.progressBar.visibility = View.GONE
            binding.btnRun.isEnabled = true
            binding.tvOutput.text = result
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
