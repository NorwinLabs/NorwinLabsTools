package com.norwinlabs.tools

import android.app.Application
import com.norwinlabs.tools.data.FirebaseAuthGate
import com.google.android.material.color.DynamicColors
import com.google.firebase.FirebaseApp
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class NorwinLabsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize Firebase
        FirebaseApp.initializeApp(this)

        // Start anonymous sign-in early so the database has an identity before Circle Share or
        // VoIP need one. Without it the database rules cannot restrict anything at all.
        FirebaseAuthGate.signInIfNeeded()

        // Apply dynamic color to all activities in the app
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}