package com.norwinlabs.tools.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.norwinlabs.tools.data.SettingsRepository
import com.norwinlabs.tools.data.ThemeMirror
import com.norwinlabs.tools.data.ThemeStartup
import com.norwinlabs.tools.data.db.NoteDao
import com.norwinlabs.tools.data.db.NorwinDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Settings store.
 *
 * Migrates the specific keys the app owns out of the legacy "norwin_prefs" file, by their
 * original names, so existing users keep their settings and their Home layout.
 */
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings",
    produceMigrations = { context ->
        listOf(
            SharedPreferencesMigration(
                context = context,
                sharedPreferencesName = LEGACY_PREFS_NAME,
                keysToMigrate = SettingsRepository.Keys.ALL_NAMES,
            )
        )
    },
)

private const val LEGACY_PREFS_NAME = "norwin_prefs"

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideSettingsDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = context.settingsDataStore

    @Provides
    @Singleton
    fun provideThemeMirror(@ApplicationContext context: Context): ThemeMirror =
        ThemeMirror { mode -> ThemeStartup.mirror(context, mode) }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): NorwinDatabase =
        Room.databaseBuilder(context, NorwinDatabase::class.java, NorwinDatabase.NAME).build()

    @Provides
    fun provideNoteDao(database: NorwinDatabase): NoteDao = database.noteDao()
}
