package com.norwinlabs.tools.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Anonymous Firebase sign-in.
 *
 * The Realtime Database is reached with no identity at all today, which means its rules cannot
 * restrict anything - see FIREBASE_SECURITY.md. Signing in anonymously gives every install a
 * stable uid so the rules can require `auth != null`, which is what stops the database being
 * readable by anyone who has the project URL.
 *
 * Sign-in is cached by the Firebase SDK, so after the first launch `currentUser` is already
 * restored by the time the app starts.
 */
object FirebaseAuthGate {

    private const val TAG = "FirebaseAuthGate"

    private val auth: FirebaseAuth?
        get() = try { FirebaseAuth.getInstance() } catch (e: Exception) {
            Log.w(TAG, "Firebase auth unavailable", e)
            null
        }

    /** The current uid, or null if sign-in has not completed yet. */
    val uid: String? get() = auth?.currentUser?.uid

    /** Starts sign-in without waiting. Called at app start so the common path is already warm. */
    fun signInIfNeeded() {
        val auth = auth ?: return
        if (auth.currentUser != null) return
        auth.signInAnonymously()
            .addOnFailureListener { Log.w(TAG, "Anonymous sign-in failed", it) }
    }

    /**
     * Suspends until signed in, returning the uid, or null if sign-in is unavailable or fails.
     * Callers should treat null as "cloud features unavailable" rather than retrying forever.
     */
    suspend fun awaitUid(): String? {
        val auth = auth ?: return null
        auth.currentUser?.uid?.let { return it }

        return suspendCancellableCoroutine { continuation ->
            auth.signInAnonymously()
                .addOnSuccessListener { result ->
                    if (continuation.isActive) continuation.resume(result.user?.uid)
                }
                .addOnFailureListener { error ->
                    Log.w(TAG, "Anonymous sign-in failed", error)
                    if (continuation.isActive) continuation.resume(null)
                }
        }
    }
}
