package com.example.openruncoach.data.samsunghealth

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import java.util.Base64
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.samsungMatchSelectionDataStore by preferencesDataStore(name = "samsung_match_selections")

/** Stores only diagnostic Samsung candidate choices; it does not alter canonical Health Connect runs. */
class SamsungMatchSelectionRepository(context: Context) {
  private val dataStore = context.applicationContext.samsungMatchSelectionDataStore

  suspend fun selectedCandidateId(healthConnectRunId: String): String? = dataStore.data
    .catch { error ->
      if (error is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw error
    }
    .map { preferences -> preferences[key(healthConnectRunId)] }
    .first()

  suspend fun selectCandidate(healthConnectRunId: String, samsungCandidateId: String) {
    dataStore.edit { preferences ->
      preferences[key(healthConnectRunId)] = samsungCandidateId
    }
  }

  private fun key(runId: String) = stringPreferencesKey(
    "selected_samsung_exercise_${Base64.getUrlEncoder().withoutPadding().encodeToString(runId.toByteArray())}"
  )
}
