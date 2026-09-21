package com.example.openruncoach.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.runSettingsDataStore by preferencesDataStore(name = "run_settings")

class RunSettingsRepository(context: Context) {
  private val dataStore = context.applicationContext.runSettingsDataStore

  val preferredSourcePackage: Flow<String?> = dataStore.data
    .catch { exception ->
      if (exception is IOException) emit(androidx.datastore.preferences.core.emptyPreferences())
      else throw exception
    }
    .map { preferences -> preferences[PREFERRED_SOURCE_PACKAGE] }

  suspend fun setPreferredSourcePackage(packageName: String?) {
    dataStore.edit { preferences ->
      if (packageName.isNullOrBlank()) preferences.remove(PREFERRED_SOURCE_PACKAGE)
      else preferences[PREFERRED_SOURCE_PACKAGE] = packageName
    }
  }

  private companion object {
    val PREFERRED_SOURCE_PACKAGE = stringPreferencesKey("preferred_workout_source_package")
  }
}
