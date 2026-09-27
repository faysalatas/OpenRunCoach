package com.example.openruncoach

data class CoachAnalysisRuntimeTrace(
  val environmentKey: String,
  val databaseIdentity: String,
  val metricsDaoIdentity: String,
  val workoutResponseRepositoryIdentity: String,
  val responseObjectIdentity: String? = null,
  val responseRunId: String? = null,
  val responseContinuity: String? = null,
)

object SimulationRuntimeTrace {
  const val enabled = true
  private var coachAnalysis: CoachAnalysisRuntimeTrace? = null

  @Synchronized
  fun recordCoachAnalysis(
    environmentKey: String,
    database: Any,
    metricsDao: Any,
    workoutResponseRepository: Any,
  ) {
    coachAnalysis = CoachAnalysisRuntimeTrace(
      environmentKey = environmentKey,
      databaseIdentity = database.identity(),
      metricsDaoIdentity = metricsDao.identity(),
      workoutResponseRepositoryIdentity = workoutResponseRepository.identity(),
    )
  }

  @Synchronized
  fun coachAnalysisSnapshot(): CoachAnalysisRuntimeTrace? = coachAnalysis

  @Synchronized
  fun recordCoachResponse(
    environmentKey: String,
    response: Any?,
    runId: String?,
    continuity: String?,
  ) {
    val existing = coachAnalysis ?: return
    if (existing.environmentKey != environmentKey) return
    coachAnalysis = existing.copy(
      responseObjectIdentity = response?.identity(),
      responseRunId = runId,
      responseContinuity = continuity,
    )
  }

  @Synchronized
  fun reset() {
    coachAnalysis = null
  }
}

private fun Any.identity() = "${javaClass.simpleName}@${System.identityHashCode(this)}"
