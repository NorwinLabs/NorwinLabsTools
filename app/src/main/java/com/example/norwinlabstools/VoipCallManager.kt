package com.example.norwinlabstools

import android.content.Context
import android.media.AudioManager
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import org.json.JSONObject
import org.webrtc.AudioTrack
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import java.util.UUID

/**
 * Audio-only VoIP calling over WebRTC, signaled through the same Firebase Realtime Database
 * Circle Share already uses (under its own top-level `voipCalls`/`voipInbox` nodes).
 *
 * Two real limitations worth knowing:
 * - There's no foreground service keeping a signaling connection alive in the background, so
 *   this only rings while the Calls screen is open on the receiving end - not a true "phone
 *   call" replacement that rings when the app is closed.
 * - Only a public STUN server is configured (no TURN server), so two devices both behind
 *   restrictive/symmetric NATs may fail to establish a connection. Fixing that would require
 *   running or paying for TURN relay infrastructure this app doesn't have.
 */
class VoipCallManager(private val context: Context) {

    companion object {
        private const val STUN_SERVER = "stun:stun.l.google.com:19302"
        private var factoryInitialized = false
    }

    interface CallListener {
        fun onIncomingCall(callId: String, callerId: String, callerName: String)
        fun onCallConnected()
        fun onCallEnded(reason: String)
        fun onError(message: String)
    }

    var listener: CallListener? = null

    private val database: DatabaseReference? by lazy {
        try { FirebaseDatabase.getInstance().reference } catch (e: Exception) { null }
    }

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var localAudioTrack: AudioTrack? = null

    private var myUserId: String? = null
    private var currentCallId: String? = null
    private var isCaller = false
    private var remoteDescriptionSet = false
    private val pendingRemoteCandidates = mutableListOf<IceCandidate>()

    private var pendingOffer: String? = null
    private var inboxRef: DatabaseReference? = null
    private var inboxListener: ValueEventListener? = null
    private var callStatusRef: DatabaseReference? = null
    private var callStatusListener: ValueEventListener? = null
    private var remoteCandidatesRef: DatabaseReference? = null
    private var remoteCandidatesListener: ChildEventListener? = null

    private fun ensureFactory() {
        if (!factoryInitialized) {
            PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
                    .createInitializationOptions()
            )
            factoryInitialized = true
        }
        if (peerConnectionFactory == null) {
            peerConnectionFactory = PeerConnectionFactory.builder().createPeerConnectionFactory()
        }
    }

    /** Starts listening for incoming calls addressed to this user's Call ID. */
    fun startListeningForCalls(myUserId: String) {
        this.myUserId = myUserId
        val ref = database?.child("voipInbox")?.child(myUserId)?.child("activeCallId") ?: return
        val valueListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val callId = snapshot.getValue(String::class.java) ?: return
                if (callId == currentCallId) return
                database?.child("voipCalls")?.child(callId)?.get()?.addOnSuccessListener { callSnap ->
                    if (callSnap.child("status").getValue(String::class.java) != "ringing") return@addOnSuccessListener
                    val callerId = callSnap.child("callerId").getValue(String::class.java) ?: return@addOnSuccessListener
                    val callerName = callSnap.child("callerName").getValue(String::class.java) ?: "Unknown"
                    val offer = callSnap.child("offer").getValue(String::class.java) ?: return@addOnSuccessListener
                    currentCallId = callId
                    isCaller = false
                    pendingOffer = offer
                    // Attach now, not just after accepting - otherwise a caller who cancels
                    // while this device is still on the ringing screen would leave it stuck,
                    // since nothing would be listening for that status change yet.
                    attachCallStatusListener(callId)
                    attachRemoteCandidatesListener(callId, remoteRole = "caller")
                    listener?.onIncomingCall(callId, callerId, callerName)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        ref.addValueEventListener(valueListener)
        inboxRef = ref
        inboxListener = valueListener
    }

    fun stopListeningForCalls() {
        inboxListener?.let { inboxRef?.removeEventListener(it) }
        inboxRef = null
        inboxListener = null
    }

    fun startCall(myUserId: String, myName: String, calleeId: String) {
        if (database == null) { listener?.onError("Cloud sync unavailable"); return }
        this.myUserId = myUserId
        val callId = UUID.randomUUID().toString()
        currentCallId = callId
        isCaller = true

        peerConnection = createPeerConnection(callId, myRole = "caller")
        if (peerConnection == null) { listener?.onError("Failed to start call engine"); return }

        localAudioTrack = createLocalAudioTrack()
        peerConnection?.addTrack(localAudioTrack, listOf("voip_stream"))

        peerConnection?.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                sdp ?: return
                peerConnection?.setLocalDescription(SimpleSdpObserver(), sdp)
                val callData = mapOf(
                    "callerId" to myUserId,
                    "callerName" to myName,
                    "calleeId" to calleeId,
                    "offer" to sdp.description,
                    "status" to "ringing",
                    "createdAt" to System.currentTimeMillis()
                )
                database?.child("voipCalls")?.child(callId)?.setValue(callData)
                database?.child("voipInbox")?.child(calleeId)?.child("activeCallId")?.setValue(callId)
                attachCallStatusListener(callId)
                attachRemoteCandidatesListener(callId, remoteRole = "callee")
            }
            override fun onCreateFailure(error: String?) {
                listener?.onError("Could not start call: $error")
            }
        }, offerConstraints())
    }

    fun acceptIncomingCall() {
        val callId = currentCallId ?: return
        val userId = myUserId ?: return
        val offerSdp = pendingOffer ?: return

        peerConnection = createPeerConnection(callId, myRole = "callee")
        if (peerConnection == null) { listener?.onError("Failed to start call engine"); return }

        localAudioTrack = createLocalAudioTrack()
        peerConnection?.addTrack(localAudioTrack, listOf("voip_stream"))

        val remoteDesc = SessionDescription(SessionDescription.Type.OFFER, offerSdp)
        peerConnection?.setRemoteDescription(object : SimpleSdpObserver() {
            override fun onSetSuccess() {
                remoteDescriptionSet = true
                drainPendingCandidates()
                peerConnection?.createAnswer(object : SimpleSdpObserver() {
                    override fun onCreateSuccess(sdp: SessionDescription?) {
                        sdp ?: return
                        peerConnection?.setLocalDescription(SimpleSdpObserver(), sdp)
                        database?.child("voipCalls")?.child(callId)?.child("answer")?.setValue(sdp.description)
                        database?.child("voipCalls")?.child(callId)?.child("status")?.setValue("accepted")
                        listener?.onCallConnected()
                    }
                    override fun onCreateFailure(error: String?) {
                        listener?.onError("Could not answer call: $error")
                    }
                }, offerConstraints())
            }
        }, remoteDesc)

        // Status/candidate listeners were already attached in startListeningForCalls when the
        // incoming call was first detected.
        database?.child("voipInbox")?.child(userId)?.child("activeCallId")?.removeValue()
    }

    fun declineIncomingCall() {
        val callId = currentCallId
        val userId = myUserId
        // Detach first so we don't process our own "declined" write through the status listener
        // that's been watching this call since it was detected as incoming.
        detachCallListeners()
        if (callId != null) {
            database?.child("voipCalls")?.child(callId)?.child("status")?.setValue("declined")
        }
        if (userId != null) {
            database?.child("voipInbox")?.child(userId)?.child("activeCallId")?.removeValue()
        }
        resetCallState()
    }

    fun endCall() {
        val callId = currentCallId
        // Detach our own listener first so we don't react to our own hangup write below, then
        // just mark the call ended - not remove the node outright. Whichever side observes the
        // "ended" status (see attachCallStatusListener) is responsible for the actual cleanup,
        // so a same-client setValue()-then-removeValue() in quick succession can't race and hide
        // the status change from whichever side didn't initiate the hangup.
        detachCallListeners()
        if (callId != null) {
            database?.child("voipCalls")?.child(callId)?.child("status")?.setValue("ended")
        }
        myUserId?.let { database?.child("voipInbox")?.child(it)?.child("activeCallId")?.removeValue() }
        closePeerConnection()
        resetCallState()
    }

    fun setMuted(muted: Boolean) {
        localAudioTrack?.setEnabled(!muted)
    }

    fun setSpeakerphoneOn(on: Boolean) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.isSpeakerphoneOn = on
    }

    /** Call when the applet screen is torn down - releases everything, active call or not. */
    fun release() {
        stopListeningForCalls()
        if (currentCallId != null) {
            endCall()
        }
        peerConnectionFactory = null
    }

    // --- Internals ---

    private fun offerConstraints() = MediaConstraints().apply {
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
    }

    private fun createPeerConnection(callId: String, myRole: String): PeerConnection? {
        ensureFactory()
        val iceServers = listOf(PeerConnection.IceServer.builder(STUN_SERVER).createIceServer())
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }
        return peerConnectionFactory?.createPeerConnection(rtcConfig, object : SimplePeerConnectionObserver() {
            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate ?: return
                database?.child("voipCalls")?.child(callId)?.child("candidates")?.child(myRole)
                    ?.push()?.setValue(serializeCandidate(candidate))
            }
            override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
                if (newState == PeerConnection.IceConnectionState.CONNECTED) {
                    listener?.onCallConnected()
                }
            }
        })
    }

    private fun createLocalAudioTrack(): AudioTrack {
        ensureFactory()
        val source = peerConnectionFactory!!.createAudioSource(MediaConstraints())
        return peerConnectionFactory!!.createAudioTrack("voip_audio_${UUID.randomUUID()}", source)
    }

    private fun attachCallStatusListener(callId: String) {
        val ref = database?.child("voipCalls")?.child(callId) ?: return
        val valueListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) return
                val status = snapshot.child("status").getValue(String::class.java)
                if (isCaller && status == "accepted" && !remoteDescriptionSet) {
                    val answerSdp = snapshot.child("answer").getValue(String::class.java) ?: return
                    val remoteDesc = SessionDescription(SessionDescription.Type.ANSWER, answerSdp)
                    peerConnection?.setRemoteDescription(object : SimpleSdpObserver() {
                        override fun onSetSuccess() {
                            remoteDescriptionSet = true
                            drainPendingCandidates()
                        }
                    }, remoteDesc)
                } else if (status == "declined") {
                    val wasCaller = isCaller
                    detachCallListeners()
                    closePeerConnection()
                    resetCallState()
                    database?.child("voipCalls")?.child(callId)?.removeValue()
                    if (wasCaller) listener?.onCallEnded("Call declined")
                } else if (status == "ended") {
                    detachCallListeners()
                    closePeerConnection()
                    resetCallState()
                    database?.child("voipCalls")?.child(callId)?.removeValue()
                    listener?.onCallEnded("Call ended")
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        ref.addValueEventListener(valueListener)
        callStatusRef = ref
        callStatusListener = valueListener
    }

    private fun attachRemoteCandidatesListener(callId: String, remoteRole: String) {
        val ref = database?.child("voipCalls")?.child(callId)?.child("candidates")?.child(remoteRole) ?: return
        val childListener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val json = snapshot.getValue(String::class.java) ?: return
                val candidate = parseCandidate(json) ?: return
                if (remoteDescriptionSet) {
                    peerConnection?.addIceCandidate(candidate)
                } else {
                    pendingRemoteCandidates.add(candidate)
                }
            }
            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        }
        ref.addChildEventListener(childListener)
        remoteCandidatesRef = ref
        remoteCandidatesListener = childListener
    }

    private fun detachCallListeners() {
        callStatusListener?.let { l -> callStatusRef?.removeEventListener(l) }
        remoteCandidatesListener?.let { l -> remoteCandidatesRef?.removeEventListener(l) }
        callStatusRef = null
        callStatusListener = null
        remoteCandidatesRef = null
        remoteCandidatesListener = null
    }

    private fun drainPendingCandidates() {
        pendingRemoteCandidates.forEach { peerConnection?.addIceCandidate(it) }
        pendingRemoteCandidates.clear()
    }

    private fun closePeerConnection() {
        peerConnection?.close()
        peerConnection = null
        localAudioTrack?.dispose()
        localAudioTrack = null
    }

    private fun resetCallState() {
        currentCallId = null
        isCaller = false
        pendingOffer = null
        remoteDescriptionSet = false
        pendingRemoteCandidates.clear()
    }

    private fun serializeCandidate(candidate: IceCandidate): String {
        return JSONObject().apply {
            put("sdpMid", candidate.sdpMid)
            put("sdpMLineIndex", candidate.sdpMLineIndex)
            put("sdp", candidate.sdp)
        }.toString()
    }

    private fun parseCandidate(json: String): IceCandidate? {
        return try {
            val obj = JSONObject(json)
            IceCandidate(obj.getString("sdpMid"), obj.getInt("sdpMLineIndex"), obj.getString("sdp"))
        } catch (e: Exception) {
            null
        }
    }

    private open class SimpleSdpObserver : SdpObserver {
        override fun onCreateSuccess(sdp: SessionDescription?) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(error: String?) {}
        override fun onSetFailure(error: String?) {}
    }

    private open class SimplePeerConnectionObserver : PeerConnection.Observer {
        override fun onSignalingChange(newState: PeerConnection.SignalingState?) {}
        override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {}
        override fun onIceConnectionReceivingChange(receiving: Boolean) {}
        override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState?) {}
        override fun onIceCandidate(candidate: IceCandidate?) {}
        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
        override fun onAddStream(stream: MediaStream?) {}
        override fun onRemoveStream(stream: MediaStream?) {}
        override fun onDataChannel(dataChannel: DataChannel?) {}
        override fun onRenegotiationNeeded() {}
    }
}
