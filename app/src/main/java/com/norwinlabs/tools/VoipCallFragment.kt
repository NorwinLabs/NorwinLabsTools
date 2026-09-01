package com.norwinlabs.tools

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.norwinlabs.tools.databinding.FragmentVoipCallBinding
import java.util.Locale

/**
 * UI for the VoIP Calling applet. Owns no call state itself - [VoipCallService] does, so calls
 * can still be detected and answered while this screen isn't open. This fragment just binds to
 * that service, mirrors its state into the four screens below, and forwards button taps.
 */
class VoipCallFragment : Fragment() {

    private var _binding: FragmentVoipCallBinding? = null
    private val binding get() = _binding!!

    private var voipService: VoipCallService? = null
    // Set true as soon as bindService() is called, not once onServiceConnected fires - the
    // connection can be legitimately unbound before it ever connects (e.g. a very fast
    // onStart/onStop), and unbindService() still needs to be called in that case too.
    private var serviceBound = false

    // Consumed on the first incoming call seen after binding, then never acted on again -
    // set from the "autoAccept" nav argument when this screen was opened via a notification's
    // Accept action, so the fragment doesn't re-trigger accept on every later rebind.
    private var autoAcceptPending = false

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

    private val callListener = object : VoipCallManager.CallListener {
        override fun onIncomingCall(callId: String, callerId: String, callerName: String) {
            activity?.runOnUiThread {
                if (_binding == null) return@runOnUiThread
                showIncomingCall(callerName)
                if (autoAcceptPending) {
                    autoAcceptPending = false
                    acceptCall()
                }
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

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val bound = (service as? VoipCallService.LocalBinder)?.getService() ?: return
            voipService = bound
            if (_binding == null) return
            binding.tvMyCallId.text = bound.myUserId
            binding.editMyName.setText(bound.myName)
            // Replays a ringing/active call, if any, straight into the callbacks above.
            bound.setUiListener(callListener)
            // Not covered by CallListener - only relevant to whichever side placed the call.
            val outgoingCalleeId = bound.callManager.getPendingOutgoingCalleeId()
            if (outgoingCalleeId != null && binding.layoutDial.visibility == View.VISIBLE) {
                binding.tvOutgoingStatus.text = "Calling $outgoingCalleeId…"
                showOutgoing()
            }
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            voipService = null
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

        autoAcceptPending = arguments?.getBoolean("autoAccept", false) ?: false

        binding.editMyName.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                voipService?.setMyName(s.toString())
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        binding.btnShareCallId.setOnClickListener { shareCallId() }
        binding.btnCall.setOnClickListener { startOutgoingCall() }
        binding.btnCancelOutgoing.setOnClickListener { hangUp() }
        binding.btnAccept.setOnClickListener { acceptCall() }
        binding.btnDecline.setOnClickListener { voipService?.callManager?.declineIncomingCall(); showDialScreen() }
        binding.btnEndCall.setOnClickListener { hangUp() }
        binding.btnToggleMute.setOnClickListener { toggleMute() }
        binding.btnToggleSpeaker.setOnClickListener { toggleSpeaker() }

        checkPermissions()
        showDialScreen()
    }

    override fun onStart() {
        super.onStart()
        val intent = Intent(requireContext(), VoipCallService::class.java)
        ContextCompat.startForegroundService(requireContext(), intent)
        requireContext().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        serviceBound = true
    }

    override fun onStop() {
        super.onStop()
        if (serviceBound) {
            voipService?.setUiListener(null)
            requireContext().unbindService(serviceConnection)
            serviceBound = false
            voipService = null
        }
        // Deliberately not stopping VoipCallService here - it keeps listening for calls in the
        // background, which is the entire point of it being a foreground service.
    }

    private fun checkPermissions() {
        val missing = mutableListOf<String>()
        if (!hasPermission(Manifest.permission.RECORD_AUDIO)) missing += Manifest.permission.RECORD_AUDIO
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasPermission(Manifest.permission.POST_NOTIFICATIONS)) {
            missing += Manifest.permission.POST_NOTIFICATIONS
        }
        if (missing.isNotEmpty()) {
            requestPermissions(missing.toTypedArray(), PERMISSION_REQUEST_CODE)
        }
    }

    private fun hasPermission(permission: String): Boolean {
        return ActivityCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasAudioPermission(): Boolean = hasPermission(Manifest.permission.RECORD_AUDIO)

    private fun startOutgoingCall() {
        val service = voipService
        if (service == null) {
            Toast.makeText(context, "Still connecting - try again in a moment", Toast.LENGTH_SHORT).show()
            return
        }
        val calleeId = binding.editCalleeId.text.toString().trim().uppercase()
        when {
            calleeId.isBlank() -> Toast.makeText(context, "Enter a Call ID", Toast.LENGTH_SHORT).show()
            calleeId == service.myUserId -> Toast.makeText(context, "That's your own Call ID", Toast.LENGTH_SHORT).show()
            !hasAudioPermission() -> {
                Toast.makeText(context, "Microphone permission is required to call", Toast.LENGTH_SHORT).show()
                checkPermissions()
            }
            else -> {
                binding.tvOutgoingStatus.text = "Calling $calleeId…"
                showOutgoing()
                service.callManager.startCall(service.myUserId, service.myName, calleeId)
            }
        }
    }

    private fun acceptCall() {
        if (!hasAudioPermission()) {
            Toast.makeText(context, "Microphone permission is required to answer", Toast.LENGTH_SHORT).show()
            checkPermissions()
            return
        }
        voipService?.callManager?.acceptIncomingCall()
    }

    private fun hangUp() {
        voipService?.callManager?.endCall()
        showDialScreen()
    }

    private fun shareCallId() {
        val myUserId = voipService?.myUserId ?: return
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "Call me on NorwinLabsTools! My Call ID: $myUserId")
        }
        startActivity(Intent.createChooser(shareIntent, "Share Call ID"))
    }

    private fun toggleMute() {
        isMuted = !isMuted
        voipService?.callManager?.setMuted(isMuted)
        binding.btnToggleMute.text = if (isMuted) "Unmute" else "Mute"
    }

    private fun toggleSpeaker() {
        isSpeakerOn = !isSpeakerOn
        voipService?.callManager?.setSpeakerphoneOn(isSpeakerOn)
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
        binding.tvInCallName.text = voipService?.callManager?.getCurrentPeerName() ?: ""
        binding.layoutDial.visibility = View.GONE
        binding.layoutOutgoing.visibility = View.GONE
        binding.layoutIncoming.visibility = View.GONE
        binding.layoutInCall.visibility = View.VISIBLE
        callStartTime = System.currentTimeMillis()
        durationHandler.post(durationTicker)
        voipService?.callManager?.setSpeakerphoneOn(false)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        durationHandler.removeCallbacks(durationTicker)
        _binding = null
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 2001
    }
}
