package com.example.openruncoach.data

import android.util.Log
import com.example.openruncoach.BuildConfig

object CompletionScheduleDiagnostics {
  const val TAG = "OpenRunCoach.CompletionSchedule"

  fun debug(message: String) {
    if (BuildConfig.DEBUG) Log.d(TAG, message)
  }

  fun error(message: String, throwable: Throwable) {
    Log.e(TAG, message, throwable)
  }
}
