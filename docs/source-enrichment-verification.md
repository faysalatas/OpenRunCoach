# Summary source and Samsung enrichment verification

These are manual steps for the user. No build, test, or device verification was executed as part of this change.

The source-selection fix touches the following files (other existing rendering/chart/workout-label changes in the working tree were preserved):

- `app/src/main/java/com/example/openruncoach/data/RunMetricsAutoRefreshCoordinator.kt`
- `app/src/main/java/com/example/openruncoach/data/RunRepository.kt`
- `app/src/main/java/com/example/openruncoach/data/RunRouteRepository.kt`
- `app/src/main/java/com/example/openruncoach/data/local/RunRouteDao.kt`
- `app/src/main/java/com/example/openruncoach/data/samsunghealth/SamsungHealthRunMetricsEnricher.kt`
- `app/src/main/java/com/example/openruncoach/domain/metrics/RunMetrics.kt`
- `app/src/main/java/com/example/openruncoach/domain/samsunghealth/SamsungHealthInspection.kt`
- `app/src/main/java/com/example/openruncoach/domain/samsunghealth/SamsungRouteSelection.kt`
- `app/src/main/java/com/example/openruncoach/ui/history/HistoryViewModel.kt`
- `app/src/main/java/com/example/openruncoach/ui/inspection/HealthDataInspectorScreen.kt`
- `app/src/main/java/com/example/openruncoach/ui/inspection/HealthDataInspectorViewModel.kt`
- `app/src/test/java/com/example/openruncoach/data/RunMetricsRefreshEngineTest.kt`
- `app/src/test/java/com/example/openruncoach/domain/metrics/RunMetricsEnrichmentTest.kt`
- `app/src/test/java/com/example/openruncoach/domain/samsunghealth/SamsungWorkoutMatcherTest.kt`
- `app/src/test/java/com/example/openruncoach/domain/samsunghealth/SamsungRouteSelectionTest.kt`
- `app/src/androidTest/java/com/example/openruncoach/data/RunRouteRepositoryTest.kt`
- `README.md`
- `docs/source-enrichment-verification.md`

Manual checklist:

1. Build manually:

   ```bash
   ./gradlew test
   ./gradlew assembleDebug
   ```

   Expected: both pass. Room persistence regressions are also supplied in `RunRouteRepositoryTest` under `androidTest`; they are not executed by `./gradlew test`.

2. Set **Preferred workout source** to the normal/default non-Samsung option. Open the Thursday run whose route previously depended on Samsung being preferred. Expected: its valid HIGH-confidence matched Samsung route is visible in both History and Run Detail. Open **Inspect Health Data → Canonical matching / raw comparison** and return a screenshot plus:

   - Canonical physical run ID
   - Summary provider-record ID and canonical summary source
   - Preferred workout source
   - Samsung matched session ID and match confidence/relationship
   - Persisted route source, point count, and canonical physical owner ID
   - Samsung HR, speed, and cadence sample counts used

3. Set **Preferred workout source = Samsung Health** and open the same physical activity. Expected: route remains available; physical owner ID and Samsung provenance are preserved. Return the same diagnostic values. Then switch back to the normal/default option and repeat. Point counts should remain comparable when the canonical time window is unchanged. If the selected summary has a genuinely different trimmed interval, only points inside that interval are eligible, so its count may legitimately differ.

4. Open **Tuesday, 29 September · 2.45 km · 20:58** and its debug/source details. If Samsung is HIGH-confidence matched to the same physical activity and contains a valid route, expect the route and History thumbnail to appear, with supported Samsung sensor enrichment. If the Samsung match is ambiguous, expect no forced route. Return a screenshot and the physical run ID, summary provider-record ID/source, Samsung session ID, confidence/relationship, route source, point count, and route owner ID. A “Possible duplicate” label alone is not permission to borrow a route.

5. Check duplicate safety: the confidently resolved Samsung/Strava representations remain one canonical History row. Two legitimate activities on the same date retain separate identities and routes.

6. Check workout-association safety: existing manually assigned workouts remain assigned before and after changing source preference.

7. Check a trimmed canonical run: compare canonical start/end with the Samsung session window. Expected: no Samsung route, HR, cadence, or speed samples outside the canonical interval reach route/charts/splits. Full-session elevation/VO₂max must not be substituted for the trimmed activity. Normal route refresh should rebuild `ROUTE_DERIVED` splits through the existing derivation pipeline. If only an older route is currently available, outdated splits may remain unavailable until that refresh succeeds.

8. Refresh the same run twice and reopen History/Run Detail. Expected: stable owner/provenance, one canonical History row, and no duplicate route/split persistence. No app-data deletion is needed. A temporary Samsung read failure should leave previously validated route evidence available inside the current canonical interval.
