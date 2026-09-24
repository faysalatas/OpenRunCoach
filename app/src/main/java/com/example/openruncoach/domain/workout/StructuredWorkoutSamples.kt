package com.example.openruncoach.domain.workout

import java.time.Instant
import java.time.LocalDate

object StructuredWorkoutSamples {
  val scheduledDate: LocalDate = LocalDate.of(2026, 9, 28)
  val createdAt: Instant = Instant.parse("2026-09-24T12:00:00Z")

  val all: List<StructuredWorkout> by lazy {
    listOf(easyRun(), recoveryRun(), longRun(), runWalk(), tempo(), threshold(), intervals(), strides(), technique())
  }

  fun easyRun() = workout(
    "sample-easy", WorkoutType.EASY_RUN, WorkoutPurpose.AEROBIC_BASE,
    listOf(single("sample-easy", 0, SegmentCategory.EASY, "Easy running", DurationCompletion(35 * 60L), RpeTarget(3, 4))),
    "Build aerobic endurance with low fatigue.",
    listOf("Keep the effort conversational", "Finish feeling able to continue"),
  )

  fun recoveryRun() = workout(
    "sample-recovery", WorkoutType.RECOVERY_RUN, WorkoutPurpose.RECOVERY,
    listOf(single("sample-recovery", 0, SegmentCategory.RECOVERY, "Recovery running", DurationCompletion(25 * 60L), RpeTarget(2, 3))),
    "Promote recovery through short, gentle movement.", listOf("Keep every minute comfortable"),
  )

  fun longRun() = workout(
    "sample-long", WorkoutType.LONG_RUN, WorkoutPurpose.ENDURANCE,
    listOf(single("sample-long", 0, SegmentCategory.EASY, "Long easy running", DistanceCompletion(8_000.0), RpeTarget(3, 4))),
    "Build endurance with sustained easy running.", listOf("Start conservatively", "Keep the effort steady"),
  )

  fun runWalk() = workout(
    "sample-run-walk", WorkoutType.RUN_WALK, WorkoutPurpose.AEROBIC_BASE,
    listOf(repeat(
      "sample-run-walk", 0, 6, "Run/walk rounds",
      segment("sample-run-walk", 0, 0, SegmentCategory.EASY, "Run", DurationCompletion(3 * 60L), RpeTarget(3, 4)),
      segment("sample-run-walk", 0, 1, SegmentCategory.WALK, "Walk", DurationCompletion(60), NoIntensityTarget),
    )),
    "Build consistent aerobic time using planned walk breaks.", listOf("Keep the run portions controlled"),
  )

  fun tempo() = workout(
    "sample-tempo", WorkoutType.TEMPO_RUN, WorkoutPurpose.LACTATE_THRESHOLD,
    listOf(
      single("sample-tempo", 0, SegmentCategory.WARMUP, "Warm-up", DurationCompletion(10 * 60L), RpeTarget(2, 3)),
      single("sample-tempo", 1, SegmentCategory.WORK, "Tempo", DurationCompletion(20 * 60L), RpeTarget(6, 7)),
      single("sample-tempo", 2, SegmentCategory.COOLDOWN, "Cool-down", DurationCompletion(10 * 60L), RpeTarget(2, 3)),
    ),
    "Practice a continuous, moderately hard effort.", listOf("Settle into a controlled rhythm"),
  )

  fun threshold() = workout(
    "sample-threshold", WorkoutType.THRESHOLD, WorkoutPurpose.LACTATE_THRESHOLD,
    listOf(
      single("sample-threshold", 0, SegmentCategory.WARMUP, "Warm-up", DurationCompletion(10 * 60L), RpeTarget(2, 3)),
      repeat(
        "sample-threshold", 1, 3, "Threshold rounds",
        segment("sample-threshold", 1, 0, SegmentCategory.WORK, "Threshold", DurationCompletion(8 * 60L), RpeTarget(7, 8)),
        segment("sample-threshold", 1, 1, SegmentCategory.RECOVERY, "Easy recovery", DurationCompletion(2 * 60L), RpeTarget(2, 3)),
      ),
      single("sample-threshold", 2, SegmentCategory.COOLDOWN, "Cool-down", DurationCompletion(10 * 60L), RpeTarget(2, 3)),
    ),
    "Improve sustained aerobic speed through controlled cruise intervals.",
    listOf("Keep the first repetition controlled", "Do not sprint early", "Finish with similar form to the first repetition"),
  )

  fun intervals() = workout(
    "sample-intervals", WorkoutType.INTERVALS, WorkoutPurpose.VO2MAX,
    listOf(
      single("sample-intervals", 0, SegmentCategory.WARMUP, "Warm-up", DurationCompletion(12 * 60L), RpeTarget(2, 3)),
      repeat(
        "sample-intervals", 1, 6, "Interval rounds",
        segment("sample-intervals", 1, 0, SegmentCategory.WORK, "Interval", DistanceCompletion(800.0), RpeTarget(7, 8)),
        segment("sample-intervals", 1, 1, SegmentCategory.RECOVERY, "Easy recovery", DurationCompletion(2 * 60L), RpeTarget(2, 3)),
      ),
      single("sample-intervals", 2, SegmentCategory.COOLDOWN, "Cool-down", DurationCompletion(10 * 60L), RpeTarget(2, 3)),
    ),
    "Develop aerobic power with repeatable efforts and deliberate recovery.", listOf("Run each repetition consistently"),
  )

  fun strides() = workout(
    "sample-strides", WorkoutType.STRIDES, WorkoutPurpose.RUNNING_ECONOMY,
    listOf(
      single("sample-strides", 0, SegmentCategory.EASY, "Easy running", DurationCompletion(25 * 60L), RpeTarget(3, 4)),
      repeat(
        "sample-strides", 1, 4, "Stride rounds",
        segment("sample-strides", 1, 0, SegmentCategory.STRIDE, "Stride", DurationCompletion(20), RpeTarget(7, 7), "Fast but relaxed; accelerate smoothly."),
        segment("sample-strides", 1, 1, SegmentCategory.RECOVERY, "Easy walk or jog", DurationCompletion(60), RpeTarget(1, 2)),
      ),
    ),
    "Practice fast, relaxed running while maintaining control.",
    listOf("Controlled acceleration", "Relax the shoulders", "This is not a maximum-effort sprint"),
  )

  fun technique() = workout(
    "sample-technique", WorkoutType.TECHNIQUE, WorkoutPurpose.TECHNIQUE,
    listOf(
      single("sample-technique", 0, SegmentCategory.WARMUP, "Easy warm-up", DurationCompletion(10 * 60L), RpeTarget(2, 3)),
      repeat(
        "sample-technique", 1, 2, "Drill rounds",
        segment("sample-technique", 1, 0, SegmentCategory.DRILL, "A-skips", OpenCompletion, NoIntensityTarget, "Complete with controlled form."),
        segment("sample-technique", 1, 1, SegmentCategory.DRILL, "Ankling", OpenCompletion, NoIntensityTarget, "Stop if form deteriorates."),
        segment("sample-technique", 1, 2, SegmentCategory.DRILL, "High knees", OpenCompletion, NoIntensityTarget, "Manual completion."),
      ),
      repeat(
        "sample-technique", 2, 4, "Stride rounds",
        segment("sample-technique", 2, 0, SegmentCategory.STRIDE, "Stride", DurationCompletion(20), RpeTarget(7, 7), "Fast but relaxed."),
        segment("sample-technique", 2, 1, SegmentCategory.RECOVERY, "Easy recovery", DurationCompletion(60), RpeTarget(1, 2)),
      ),
      single("sample-technique", 3, SegmentCategory.COOLDOWN, "Easy cool-down", DurationCompletion(10 * 60L), RpeTarget(2, 3)),
    ),
    "Practice general movement skills without cadence prescriptions.", listOf("Prioritize smooth movement", "Quality matters more than speed"),
  )

  private fun workout(
    id: String,
    type: WorkoutType,
    purpose: WorkoutPurpose,
    blocks: List<WorkoutBlock>,
    explanation: String,
    cues: List<String>,
  ) = PlannedWorkout(id, scheduledDate, type, type.displayName(), purpose, blocks, explanation, cues, createdAt, origin = WorkoutOrigin.SIMULATION)

  private fun single(
    workoutId: String,
    order: Int,
    category: SegmentCategory,
    name: String,
    completion: SegmentCompletion,
    intensity: IntensityTarget,
  ) = SingleSegmentBlock(
    "$workoutId:block:$order", order,
    segment(workoutId, order, 0, category, name, completion, intensity),
  )

  private fun repeat(
    workoutId: String,
    order: Int,
    repetitions: Int,
    label: String,
    vararg segments: WorkoutSegment,
  ) = RepeatBlock("$workoutId:block:$order", order, repetitions, segments.toList(), label)

  private fun segment(
    workoutId: String,
    blockOrder: Int,
    order: Int,
    category: SegmentCategory,
    name: String,
    completion: SegmentCompletion,
    intensity: IntensityTarget,
    notes: String? = null,
  ) = WorkoutSegment("$workoutId:segment:$blockOrder:$order", order, category, name, completion, intensity, notes = notes)
}
