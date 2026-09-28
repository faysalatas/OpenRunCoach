# Goal Strategy verification

## Scope and status

GoalStrategy is derived by the shared production `GoalStrategyResolver` from an active `TrainingGoal` and rebuilt `AthleteState`. It does not prescribe training, evaluate goal completion, or use plan dates or acute readiness.

Device testing was stopped at the user's request. The GS1–GS8 repository integration test had already passed on SM-S948B, including identical rebuilds and D/E/M continuity checks. The manual screen checks below remain for the user; screenshots of the new strategy scenarios have not been captured. Values below are the expected outputs, corroborated by the repository test (with a subsequent singular/plural wording correction for GS1).

The connected-test runner uninstalled the app during cleanup and it was reinstalled. This may have removed app-local data, and preservation of existing on-device real history could not be verified. Health Connect source records were not modified. Do not interpret the passing isolated repository tests as verification that real device history was preserved.

## Steps common to GS1–GS8

1. Build with `./gradlew assembleDebug` and install the debug APK as an update.
2. Open **Settings → Developer Tools → Simulation**.
3. Scroll to the named GS scenario and tap its **Load scenario** button. The existing environment switch returns to Home.
4. Reopen **Settings → Simulation** and wait for the rebuild to finish.
5. Record the Goal Strategy card, including all reasons (scroll as needed), and the Athlete State card. Capture screenshots locally if desired.
6. Compare with the exact expected values below. Every GS scenario retains an **Active** goal. These are interpretation confidence levels, not chances of success.

Capability names in the tables are enum values. The UI displays the same names as sentence-case words with spaces.

| Test | Goal | Confidence | Primary | Secondary | Supporting |
| --- | --- | --- | --- | --- | --- |
| GS1 | 5K · Complete distance | LOW | CONTINUOUS_RUNNING, DISTANCE_TOLERANCE | AEROBIC_BASE, WEEKLY_VOLUME | None |
| GS2 | 5K · Complete distance | MEDIUM | DISTANCE_TOLERANCE, AEROBIC_BASE | CONTINUOUS_RUNNING, WEEKLY_VOLUME | None |
| GS3 | 5K · Improve performance | HIGH | THRESHOLD, RACE_SPECIFIC_ENDURANCE | AEROBIC_BASE, RUNNING_ECONOMY | WEEKLY_VOLUME, SPEED |
| GS4 | 5K · Target time | HIGH | THRESHOLD, RACE_SPECIFIC_ENDURANCE | AEROBIC_BASE, RUNNING_ECONOMY | WEEKLY_VOLUME, SPEED |
| GS5 | 10K · Complete distance | HIGH | DISTANCE_TOLERANCE, AEROBIC_BASE | CONTINUOUS_RUNNING, WEEKLY_VOLUME | None |
| GS6 | Half Marathon · Complete distance | HIGH | DISTANCE_TOLERANCE, AEROBIC_BASE | WEEKLY_VOLUME, RECOVERY_TOLERANCE | CONTINUOUS_RUNNING, RACE_SPECIFIC_ENDURANCE |
| GS7 | 5K · Complete distance | LOW | CONTINUOUS_RUNNING, DISTANCE_TOLERANCE | AEROBIC_BASE, WEEKLY_VOLUME | None |
| GS8 | 10K · Complete distance | MEDIUM | DISTANCE_TOLERANCE, AEROBIC_BASE | CONTINUOUS_RUNNING, WEEKLY_VOLUME | None |

| Test | Longest completed | Longest continuous | Longest tolerated | Tolerated sessions | 28-day volume | Frequency (UI) | Latest cost | Readiness |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| GS1 | 2.50 km | Unknown | 2.50 km | 1 | 2.50 km | 0.3 runs/week | NORMAL | NORMAL |
| GS2 | 4.80 km | 4.80 km | 4.80 km | 4 | 16.80 km | 1.0 runs/week | NORMAL | NORMAL |
| GS3 | 8.00 km | 8.00 km | 8.00 km | 5 | 29.50 km | 1.3 runs/week | NORMAL | NORMAL |
| GS4 | 8.00 km | 8.00 km | 8.00 km | 5 | 29.50 km | 1.3 runs/week | NORMAL | NORMAL |
| GS5 | 6.00 km | 6.00 km | 6.00 km | 5 | 27.50 km | 1.3 runs/week | NORMAL | NORMAL |
| GS6 | 8.00 km | 8.00 km | 8.00 km | 5 | 29.50 km | 1.3 runs/week | NORMAL | NORMAL |
| GS7 | Unknown | Unknown | Unknown | 0 | 1.20 km | 0.3 runs/week | Absent | UNKNOWN |
| GS8 | 6.00 km | 6.00 km | 6.00 km | 4 | 27.50 km | 1.3 runs/week | HIGH | CONSERVATIVE |

### TEST 1 — GS1 Beginner 5K Complete

Verify short completed distance remains distinct from unknown continuity, with no primary Speed. Record Goal Strategy and Athlete State screenshots.

Exact reasons, in order:

1. The goal is continuous 5.00 km completion; distance tolerance and aerobic development take precedence over speed.
2. Continuous-running capability is insufficiently observed for the 5.00 km goal; missing evidence does not establish inability.
3. Completed distance of 2.50 km is separate from demonstrated continuous distance.
4. 1 tolerated aerobic session provides distance evidence up to 2.50 km.
5. Recent training totals 2.50 km in 28 days at 0.3 runs/week.
6. The observed volume or frequency provides limited repeated exposure for this distance; aerobic consistency and weekly volume remain relevant, without prescribing an increase.
7. Recent relevant observations are limited, so these capability priorities have low interpretation confidence.

### TEST 2 — GS2 Nearly Complete 5K

Verify **Active** goal status and **4.80 km** longest continuous distance. Capture Goal Strategy and the distance evidence. The existing GoalCompletionEvaluator is exercised by this fixture and must not complete the 5K goal.

Exact reasons:

1. The goal is continuous 5.00 km completion; distance tolerance and aerobic development take precedence over speed.
2. Continuous running has been demonstrated to 4.80 km for the 5.00 km goal.
3. 4 tolerated aerobic sessions provide distance evidence up to 4.80 km.
4. Observed continuity and repeated aerobic tolerance are close to or beyond the goal distance; consolidate aerobic consistency while retaining completion-directed distance and continuity priorities.
5. Recent training totals 16.80 km in 28 days at 1.0 runs/week.
6. The observed volume or frequency provides limited repeated exposure for this distance; aerobic consistency and weekly volume remain relevant, without prescribing an increase.

### TEST 3 — GS3 5K Improve Performance

Verify established **8.00 km** continuous/tolerated evidence, **High** confidence, and performance capability priorities that differ from GS1. Capture the strategy, including reasons.

Exact reasons:

1. The goal is faster 5.00 km performance; threshold, running economy, and race-specific endurance are relevant development capabilities.
2. Continuous running has been demonstrated to 8.00 km for the 5.00 km goal.
3. 5 tolerated aerobic sessions provide distance evidence up to 8.00 km.
4. Repeated aerobic tolerance and continuous distance support moving beyond first-distance development toward 5.00 km performance.
5. Athlete State does not establish current threshold or speed capability; these priorities describe goal demands, not measured pace deficits or permission for hard workouts.
6. Recent training totals 29.50 km in 28 days at 1.3 runs/week.
7. The observed volume or frequency provides limited repeated exposure for this distance; aerobic consistency and weekly volume remain relevant, without prescribing an increase.

### TEST 4 — GS4 5K Target 30:00

Verify **Target time: 30:00** and **Target pace · goal context only: 6:00 min/km**. Capture these labels and the capability lists. The fixture's observed easy pace is **480 s/km (8:00/km)**, not the 360 s/km target; the strategy itself exposes no training-pace prescription. No current threshold pace is inferred.

Exact reasons are GS3 reasons 2–7, preceded by:

1. The 5.00 km target time is goal context only; it does not establish current easy, threshold, or sustainable race pace.

### TEST 5 — GS5 10K Complete

Verify all three distance observations are **6.00 km**, with Distance tolerance and Aerobic base primary. Capture both cards. No primary Speed.

Exact reasons:

1. The goal is continuous 10.00 km completion; distance tolerance and aerobic development take precedence over speed.
2. Continuous running has been demonstrated to 6.00 km for the 10.00 km goal.
3. 5 tolerated aerobic sessions provide distance evidence up to 6.00 km.
4. Recent training totals 27.50 km in 28 days at 1.3 runs/week.
5. The observed volume or frequency provides limited repeated exposure for this distance; aerobic consistency and weekly volume remain relevant, without prescribing an increase.

### TEST 6 — GS6 Half Marathon

Verify Weekly volume and Recovery tolerance are secondary; Continuous running and Race specific endurance are supporting. Compare with GS1 to confirm different distance demands. Capture the strategy.

Exact reasons:

1. The goal is continuous 21.10 km completion; distance tolerance and aerobic development take precedence over speed.
2. Continuous running has been demonstrated to 8.00 km for the 21.10 km goal.
3. 5 tolerated aerobic sessions provide distance evidence up to 8.00 km.
4. Half-marathon-length demands add weekly volume tolerance, recovery tolerance, and sustained aerobic durability to distance development.
5. Recent training totals 29.50 km in 28 days at 1.3 runs/week.
6. The observed volume or frequency provides limited repeated exposure for this distance; aerobic consistency and weekly volume remain relevant, without prescribing an increase.

### TEST 7 — GS7 Missing Evidence

Verify **Low** confidence and unknown continuous/tolerated distances. No inferred inability or pace capability. Capture the strategy and its uncertainty explanation.

Exact reasons:

1. The goal is continuous 5.00 km completion; distance tolerance and aerobic development take precedence over speed.
2. Continuous-running capability is insufficiently observed for the 5.00 km goal; missing evidence does not establish inability.
3. Comfortable aerobic distance tolerance is insufficiently observed; priorities remain provisional.
4. Recent training totals 1.20 km in 28 days at 0.3 runs/week.
5. The observed volume or frequency provides limited repeated exposure for this distance; aerobic consistency and weekly volume remain relevant, without prescribing an increase.
6. Recent relevant observations are limited, so these capability priorities have low interpretation confidence.

### TEST 8 — GS8 High-Cost Run Boundary

Verify **latest High** session cost and **Conservative** readiness in Athlete State, alongside the same 10K capability lists as GS5. Capture both cards. The four tolerated historical aerobic sessions remain usable evidence; the latest hard run is not counted as comfortable tolerance.

Exact reasons are GS5 reasons 1–5, with reason 3 replaced by:

3. 4 tolerated aerobic sessions provide distance evidence up to 6.00 km.

### TEST 9 — Determinism

1. Load GS5 and record all strategy fields.
2. Tap **Rebuild Athlete State**.
3. Leave and reopen Simulation without changing inputs.
4. Compare every priority, confidence, and reason with the first result.

Exact expected before **and** after:

```text
primaryCapabilities=[DISTANCE_TOLERANCE, AEROBIC_BASE]
secondaryCapabilities=[CONTINUOUS_RUNNING, WEEKLY_VOLUME]
supportingCapabilities=[]
confidence=HIGH
reasons=[
  The goal is continuous 10.00 km completion; distance tolerance and aerobic development take precedence over speed.
  Continuous running has been demonstrated to 6.00 km for the 10.00 km goal.
  5 tolerated aerobic sessions provide distance evidence up to 6.00 km.
  Recent training totals 27.50 km in 28 days at 1.3 runs/week.
  The observed volume or frequency provides limited repeated exposure for this distance; aerobic consistency and weekly volume remain relevant, without prescribing an increase.
]
```

The repository integration test also compared complete snapshots across two rebuilds for every GS scenario and passed. Manual screen reopen verification remains for the user.

### TEST 10 — Existing regressions

1. Load D. Open its run → Coach Analysis: continuity **Continuous**; rebuild Athlete State: longest continuous **5.00 km**.
2. Load E. Coach Analysis continuity **Sustained walking/stopped interval**; no demonstrated continuous 5K.
3. Load M. No checkout/structured response is fabricated. Athlete State has **1 unknown** continuity observation; longest continuous remains **Unknown**.
4. In Simulation, open **Home → Plan → Home**, switch weeks/days, and reopen Settings. Verify the selected destination actually changes and displays the scenario's runs/plan.
5. Select **Exit Simulation**. Open real History and Plan, then Home. Verify any available real records load. On the development device used in this turn, existing-history preservation remains unverified because of the test-runner cleanup described above; re-importing Health Connect cannot recreate app-only checkouts/goals if they were removed.

D/E/M data-path assertions passed in the repository test. The remaining UI navigation/history checks are intentionally left for the user.
