package com.norwinlabs.tools.data

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

/**
 * Applies the saved theme before the first Activity is created.
 *
 * setDefaultNightMode was only ever called from the Settings change listener, so the choice held
 * for the life of that process and then reverted to follow-system on the next cold start - a
 * user who picked Dark got Light back every morning.
 *
 * The value lives in DataStore like every other setting, but DataStore reads are asynchronous and
 * Application.onCreate needs an answer before the first frame. Blocking there would cost startup
 * time; applying it asynchronously would draw the wrong theme and then recreate the Activity,
 * which is a visible flash. So the theme - and only the theme - is mirrored to a one-key
 * SharedPreferences file that can be read synchronously at startup, with DataStore remaining the
 * source of truth that Settings reads and writes.
 */
object ThemeStartup {

    private const val MIRROR_PREFS = "theme_startup"
    private const val KEY_MODE = "night_mode"

    /** Call from Application.onCreate, before any Activity exists. */
    fun apply(context: Context) {
        val mirror = context.getSharedPreferences(MIRROR_PREFS, Context.MODE_PRIVATE)
        if (!mirror.contains(KEY_MODE)) return
        AppCompatDelegate.setDefaultNightMode(
            mirror.getInt(KEY_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        )
    }

    /** Kept in step by SettingsRepository whenever the stored theme changes. */
    fun mirror(context: Context, mode: Int) {
        context.getSharedPreferences(MIRROR_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_MODE, mode)
            .apply()
    }
}
