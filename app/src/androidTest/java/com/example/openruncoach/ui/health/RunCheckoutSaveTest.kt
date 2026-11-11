package com.example.openruncoach.ui.health

import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.openruncoach.OpenRunCoachEnvironment
import com.example.openruncoach.data.*
import com.example.openruncoach.data.local.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

/** Actual ViewModel/repositories with a synthetic in-memory database; no provider sync. */
class RunCheckoutSaveTest {
  @Test fun sameViewModelRejectsInvalidInputAndUpdatesOneCheckout() = runBlocking {
    val context=ApplicationProvider.getApplicationContext<android.content.Context>()
    val db=Room.inMemoryDatabaseBuilder(context,OpenRunCoachDatabase::class.java).build()
    val clock=RunDetailPreviewData.clock
    val run=RunDetailPreviewData.state().run!!
    db.runDao().upsertAll(listOf(RunEntity(run.healthConnectRecordId,run.startTime.toEpochMilli(),run.endTime.toEpochMilli(),
      run.durationSeconds,run.distanceMeters,run.averageHeartRate,run.maxHeartRate,run.averagePaceSecondsPerKm,clock.millis())))
    val environment=OpenRunCoachEnvironment("checkout-test",db,clock,true)
    val feedback=RunFeedbackRepository(db,clock,environment.profileRepository())
    val store=ViewModelStore()
    val vm=withContext(Dispatchers.Main) {
      RunDetailViewModel(run.healthConnectRecordId,
        RunRepository(db.runDao(),HealthConnectRepository(context),RunSettingsRepository(context),clock=clock,postRunReconciler=environment.postRunReconciler()),
        feedback,RunMetricsRepository(db.runMetricsDao()),RunRouteRepository(db.runRouteDao()),null).also { store.put("checkout",it) }
    }
    suspend fun form(rpe:String,readiness:String) = withContext(Dispatchers.Main) {
      vm.editFeedback();vm.updateForm { it.copy(rpe=rpe,readiness=readiness,notes="  Keep this note  ") };vm.saveFeedback()
    }
    try {
      withTimeout(10_000) { vm.uiState.first { it.runLoaded && it.feedbackLoaded && it.run!=null } }
      for(rpe in listOf("0","11")) {
        form(rpe,"");assertEquals("RPE must be between 1 and 10.",vm.uiState.value.error)
        assertNull(feedback.getFeedbackForRun(run.healthConnectRecordId))
      }
      for(readiness in listOf("0","6")) {
        form("4",readiness);assertEquals("Readiness must be between 1 and 5.",vm.uiState.value.error)
        assertNull(feedback.getFeedbackForRun(run.healthConnectRecordId))
      }
      for((rpe,readiness) in listOf("bad" to "", "4" to "1.5")) {
        form(rpe,readiness);assertEquals("Enter a whole number for RPE and readiness.",vm.uiState.value.error)
      }
      form("1","1")
      val first=withTimeout(10_000) { vm.uiState.first { it.saved && !it.isSaving }.feedback!! }
      assertEquals(1,first.rpe);assertEquals(1,first.readiness);assertEquals("Keep this note",first.notes)
      form("10","5")
      val second=withTimeout(10_000) { vm.uiState.first { it.saved && it.feedback?.rpe==10 }.feedback!! }
      assertEquals(first.id,second.id);assertEquals(first.createdAt,second.createdAt)
      assertEquals(5,second.readiness);assertEquals(1,feedback.getAllFeedback().size)
      withContext(Dispatchers.Main) { vm.updateForm { it.copy(rpe="3",notes="unsaved") };vm.cancelFeedbackEdit() }
      assertEquals("10",vm.uiState.value.form.rpe);assertEquals("Keep this note",vm.uiState.value.form.notes)
      assertEquals(second,feedback.getFeedbackForRun(run.healthConnectRecordId))
    } finally { withContext(Dispatchers.Main) { store.clear() }; db.close() }
  }
}
