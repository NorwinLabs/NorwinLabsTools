package com.example.norwinlabstools.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.example.norwinlabstools.data.SettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Settings store.
 *
 * Only the settings keys are migrated out of the legacy "norwin_prefs" file - the Home screen's
 * saved tool layout still lives there and is left alone, so this migration must never be widened
 * to the whole file.
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
}
