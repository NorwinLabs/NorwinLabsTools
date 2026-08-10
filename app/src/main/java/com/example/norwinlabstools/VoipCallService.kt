package com.example.norwinlabstools

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.util.UUID

/**
 * Foreground service that owns the single [VoipCallManager] instance for the app, so an incoming
 * call can be detected and shown - via a notification with Accept/Decline actions - even while
 * [VoipCallFragment] isn't on screen, or the app is backgrounded entirely.
 *
 * Started (not just bound) from [VoipCallFragment] the first time the user opens the VoIP Calling
 * screen, so it keeps running and listening after they navigate away. Declared with
 * foregroundServiceType="specialUse" (API 34+) since none of the built-in types fit: this isn't a
 * real telephony call ("phoneCall" requires ConnectionService/MANAGE_OWN_CALLS integration this
 * app doesn't have), and it needs to keep listening while backgrounded, not just while in use
 * ("microphone" can't even be started from the background).
 */
class VoipCallService : Service() {

    inner class LocalBinder : Binder() {
        fun getService(): VoipCallService = this@VoipCallService
    }

    private val binder = LocalBinder()

    lateinit var callManager: VoipCallManager
        private set

    private var uiListener: VoipCallManager.CallListener? = null

    private lateinit var notificationManager: NotificationManager
    private lateinit var prefs: SharedPreferences

    var myUserId: String = ""
        private set
    var myName: String = ""
        private set

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NotificationManager::class.java)
        createNotificationChannel()

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        myUserId = prefs.getString(KEY_USER_ID, null) ?: UUID.randomUUID().toString().take(6).uppercase().also {
            prefs.edit().putString(KEY_USER_ID, it).apply()
        }
        myName = prefs.getString(KEY_MY_NAME, null) ?: "User $myUserId"

        callManager = VoipCallManager(applicationContext)
        callManager.listener = object : VoipCallManager.CallListener {
            override fun onIncomingCall(callId: String, callerId: String, callerName: String) {
                showIncomingCallNotification(callerName)
                uiListener?.onIncomingCall(callId, callerId, callerName)
            }
            override fun onCallConnected() {
                showOngoingCallNotification()
                uiListener?.onCallConnected()
            }
            override fun onCallEnded(reason: String) {
                showListeningNotification()
                uiListener?.onCallEnded(reason)
            }
            override fun onError(message: String) {
                uiListener?.onError(message)
            }
        }

        // Must be called synchronously within onCreate (which runs as part of the
        // startForegroundService() startup path) or the system kills the service.
        startForeground(NOTIFICATION_ID, buildListeningNotification())
        callManager.startListeningForCalls(myUserId)
    }

    /** Persists a new display name so it survives a service restart, same as [myUserId]. */
    fun setMyName(name: String) {
        myName = name
        prefs.edit().putString(KEY_MY_NAME, name).apply()
    }

    /**
     * Registers [callListener] for future call events, and immediately replays whatever's
     * already happening - a ringing incoming call, or an already-connected one - so UI that
     * binds in mid-call catches up instead of showing a stale dial screen. Pass null when the
     * UI is going away; that does not stop the service from listening.
     */
    fun setUiListener(callListener: VoipCallManager.CallListener?) {
        uiListener = callListener
        val pending = callManager.getPendingIncomingCall()
        if (callListener != null && pending != null) {
            callListener.onIncomingCall(pending.callId, pending.callerId, pending.callerName)
        } else if (callListener != null && callManager.isCallActive()) {
            callListener.onCallConnected()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DECLINE) {
            callManager.declineIncomingCall()
            showListeningNotification()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        callManager.release()
        super.onDestroy()
    }

    // --- Notifications ---

    private fun createNotificationChannel() {
        // Two channels, not one: the "listening"/"in call" status notification should stay
        // silent (it's just there to satisfy the foreground service requirement and let the
        // user get back to the call), while an actual incoming call needs to alert loudly. On
        // API 26+, channel importance overrides Notification.Builder.setPriority(), so sharing
        // one HIGH-importance channel between them would make the silent status notification
        // pop up as a heads-up alert with sound every time it's (re)posted.
        notificationManager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID_STATUS, "VoIP Status", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows while listening for calls or while a call is in progress"
            }
        )
        notificationManager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID_INCOMING, "Incoming Calls", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Alerts you to an incoming VoIP call"
            }
        )
    }

    private fun openAppPendingIntent(autoAccept: Boolean): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_VOIP, true)
            putExtra(EXTRA_AUTO_ACCEPT, autoAccept)
        }
        // Distinct request codes so the "open" and "accept" PendingIntents don't collide.
        return PendingIntent.getActivity(
            this, if (autoAccept) 1 else 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun declinePendingIntent(): PendingIntent {
        val intent = Intent(this, VoipCallService::class.java).apply { action = ACTION_DECLINE }
        return PendingIntent.getService(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildListeningNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID_STATUS)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle("VoIP Calling")
            .setContentText("Listening for incoming calls · Your ID: $myUserId")
            .setContentIntent(openAppPendingIntent(autoAccept = false))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun showListeningNotification() {
        notificationManager.notify(NOTIFICATION_ID, buildListeningNotification())
    }

    private fun showIncomingCallNotification(callerName: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID_INCOMING)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle("Incoming call")
            .setContentText("$callerName is calling…")
            .setContentIntent(openAppPendingIntent(autoAccept = false))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Decline", declinePendingIntent())
            .addAction(android.R.drawable.ic_menu_call, "Accept", openAppPendingIntent(autoAccept = true))
            .build()
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun showOngoingCallNotification() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID_STATUS)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle("VoIP call in progress")
            .setContentText("Tap to return to the call")
            .setContentIntent(openAppPendingIntent(autoAccept = false))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .build()
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val CHANNEL_ID_STATUS = "voip_status"
        private const val CHANNEL_ID_INCOMING = "voip_incoming"
        private const val NOTIFICATION_ID = 4001
        const val ACTION_DECLINE = "com.example.norwinlabstools.action.VOIP_DECLINE"
        const val EXTRA_OPEN_VOIP = "open_voip"
        const val EXTRA_AUTO_ACCEPT = "auto_accept"
        private const val PREFS_NAME = "voip_prefs"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_MY_NAME = "my_name"
    }
}
