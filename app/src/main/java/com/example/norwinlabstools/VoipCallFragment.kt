package com.example.norwinlabstools

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import com.example.norwinlabstools.databinding.FragmentVoipCallBinding
import java.util.Locale
import java.util.UUID

class VoipCallFragment : Fragment() {

    private var _binding: FragmentVoipCallBinding? = null
    private val binding get() = _binding!!

    private lateinit var callManager: VoipCallManager

    private val prefsName = "voip_prefs"
    private val keyUserId = "user_id"
    private val keyMyName = "my_name"

    private var myUserId: String = ""
    private var myName: String = ""

    // The person on the other end of the current/pending call, for the in-call screen title.
    // On the callee side this is their real display name (known from the incoming offer); on
    // the caller side it's just the Call ID dialed, since this app has no way to learn the
    // callee's display name before they've answered.
    private var currentPeerName: String = ""

    private var isMuted = false
    private var isSpeakerOn = false
    private var callStartTime = 0L
    private val durationHandler = Handler(Looper.getMainLooper())

    private val durationTicker = object : Runnable {
        override fun run() {
            val elapsedSeconds = (System.currentTimeMillis() - callStartTime) / 1000
            binding.tvCallDuration.text = String.format(Locale.US, "%02d:%02d", elapsedSeconds / 60, elapsedSeconds % 60)
            durationHandler.postDelayed(this, 1000)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVoipCallBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loadUserData()

        callManager = VoipCallManager(requireContext())
        callManager.listener = object : VoipCallManager.CallListener {
            override fun onIncomingCall(callId: String, callerId: String, callerName: String) {
                activity?.runOnUiThread {
                    if (_binding == null) return@runOnUiThread
                    currentPeerName = callerName
                    showIncomingCall(callerName)
                }
            }
            override fun onCallConnected() {
                activity?.runOnUiThread { if (_binding != null) showInCall() }
            }
            override fun onCallEnded(reason: String) {
                activity?.runOnUiThread {
                    if (_binding == null) return@runOnUiThread
                    Toast.makeText(context, reason, Toast.LENGTH_SHORT).show()
                    showDialScreen()
                }
            }
            override fun onError(message: String) {
                activity?.runOnUiThread {
                    if (_binding == null) return@runOnUiThread
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    showDialScreen()
                }
            }
        }

        binding.tvMyCallId.text = myUserId
        binding.editMyName.setText(myName)
        binding.editMyName.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                myName = s.toString()
                requireContext().getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                    .edit().putString(keyMyName, myName).apply()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        binding.btnShareCallId.setOnClickListener { shareCallId() }
        binding.btnCall.setOnClickListener { startOutgoingCall() }
        binding.btnCancelOutgoing.setOnClickListener { hangUp() }
        binding.btnAccept.setOnClickListener { acceptCall() }
        binding.btnDecline.setOnClickListener { callManager.declineIncomingCall(); showDialScreen() }
        binding.btnEndCall.setOnClickListener { hangUp() }
        binding.btnToggleMute.setOnClickListener { toggleMute() }
        binding.btnToggleSpeaker.setOnClickListener { toggleSpeaker() }

        checkAudioPermission()
        callManager.startListeningForCalls(myUserId)
        showDialScreen()
    }

    private fun loadUserData() {
        val prefs = requireContext().getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        myUserId = prefs.getString(keyUserId, null) ?: UUID.randomUUID().toString().take(6).uppercase().also {
            prefs.edit().putString(keyUserId, it).apply()
        }
        myName = prefs.getString(keyMyName, null) ?: "User $myUserId"
    }

    private fun checkAudioPermission() {
        if (!hasAudioPermission()) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), AUDIO_PERMISSION_REQUEST_CODE)
        }
    }

    private fun hasAudioPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    private fun startOutgoingCall() {
        val calleeId = binding.editCalleeId.text.toString().trim().uppercase()
        when {
            calleeId.isBlank() -> Toast.makeText(context, "Enter a Call ID", Toast.LENGTH_SHORT).show()
            calleeId == myUserId -> Toast.makeText(context, "That's your own Call ID", Toast.LENGTH_SHORT).show()
            !hasAudioPermission() -> {
                Toast.makeText(context, "Microphone permission is required to call", Toast.LENGTH_SHORT).show()
                checkAudioPermission()
            }
            else -> {
                currentPeerName = calleeId
                binding.tvOutgoingStatus.text = "Calling $calleeId…"
                showOutgoing()
                callManager.startCall(myUserId, myName, calleeId)
            }
        }
    }

    private fun acceptCall() {
        if (!hasAudioPermission()) {
            Toast.makeText(context, "Microphone permission is required to answer", Toast.LENGTH_SHORT).show()
            checkAudioPermission()
            return
        }
        callManager.acceptIncomingCall()
    }

    private fun hangUp() {
        callManager.endCall()
        showDialScreen()
    }

    private fun shareCallId() {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "Call me on NorwinLabsTools! My Call ID: $myUserId")
        }
        startActivity(Intent.createChooser(shareIntent, "Share Call ID"))
    }

    private fun toggleMute() {
        isMuted = !isMuted
        callManager.setMuted(isMuted)
        binding.btnToggleMute.text = if (isMuted) "Unmute" else "Mute"
    }

    private fun toggleSpeaker() {
        isSpeakerOn = !isSpeakerOn
        callManager.setSpeakerphoneOn(isSpeakerOn)
        binding.btnToggleSpeaker.text = if (isSpeakerOn) "Speaker Off" else "Speaker"
    }

    private fun showDialScreen() {
        durationHandler.removeCallbacks(durationTicker)
        binding.layoutDial.visibility = View.VISIBLE
        binding.layoutOutgoing.visibility = View.GONE
        binding.layoutIncoming.visibility = View.GONE
        binding.layoutInCall.visibility = View.GONE
        binding.editCalleeId.setText("")
        isMuted = false
        isSpeakerOn = false
        binding.btnToggleMute.text = "Mute"
        binding.btnToggleSpeaker.text = "Speaker"
        currentPeerName = ""
    }

    private fun showOutgoing() {
        binding.layoutDial.visibility = View.GONE
        binding.layoutOutgoing.visibility = View.VISIBLE
        binding.layoutIncoming.visibility = View.GONE
        binding.layoutInCall.visibility = View.GONE
    }

    private fun showIncomingCall(callerName: String) {
        binding.tvIncomingCaller.text = "$callerName is calling…"
        binding.layoutDial.visibility = View.GONE
        binding.layoutOutgoing.visibility = View.GONE
        binding.layoutIncoming.visibility = View.VISIBLE
        binding.layoutInCall.visibility = View.GONE
    }

    private fun showInCall() {
        if (binding.layoutInCall.visibility == View.VISIBLE) return
        binding.tvInCallName.text = currentPeerName
        binding.layoutDial.visibility = View.GONE
        binding.layoutOutgoing.visibility = View.GONE
        binding.layoutIncoming.visibility = View.GONE
        binding.layoutInCall.visibility = View.VISIBLE
        callStartTime = System.currentTimeMillis()
        durationHandler.post(durationTicker)
        callManager.setSpeakerphoneOn(false)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        durationHandler.removeCallbacks(durationTicker)
        callManager.release()
        _binding = null
    }

    companion object {
        private const val AUDIO_PERMISSION_REQUEST_CODE = 2001
    }
}
