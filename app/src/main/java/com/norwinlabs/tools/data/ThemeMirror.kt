package com.norwinlabs.tools.data

/**
 * Writes the theme to the synchronous startup mirror.
 *
 * An interface rather than a Context on the repository: it keeps SettingsRepository free of
 * Android dependencies, so its tests run on the JVM without Robolectric.
 */
fun interface ThemeMirror {
    fun write(mode: Int)
}
