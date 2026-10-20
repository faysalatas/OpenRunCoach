# Repository workflow

Use one focused branch and pull request per actionable issue. Read the issue and AGENTS.md first, inspect current code, and avoid repeating fixes already made locally or in another PR. Link the issue in the PR and record exact test outcomes. Implementation, automated checks and phone verification are different evidence.

Recommended board statuses: Backlog, Ready, In progress, Review, Done. Start with issues #1 and #2. Issue #7 is the backlog index. Break larger coaching issues into independently reviewable steps; do not close a broad issue when only one step lands.

## SDK-independent Core CI

`.github/workflows/core-ci.yml` runs on PRs targeting main, pushes to main and manual dispatch. It uses JDK 17 and the committed Gradle wrapper:

```bash
./gradlew --no-daemon -p ci/core-tests test
```

`ci/core-tests` is an isolated Kotlin/JVM verification build. It compiles the actual production domain source directory and existing SDK-independent domain tests directly. It does not copy/stub production logic, configure the Android app or change runtime architecture. No Samsung SDK, private host, secrets or credentials are required. Gradle/Maven dependency downloads still require network access.

The initial selection contains 29 domain test source files. Two suites import data-layer code and are explicitly excluded: `metrics/RunMetricsEnrichmentTest.kt` and `workout/WorkoutResponseEvaluatorTest.kt`. They remain in the normal app suite and are not claimed as covered here. New pure domain tests are included automatically. Keep the standalone Kotlin version aligned with the app version catalog.

This verifies domain compilation and selected tests. It does **not** compile HomeViewModel, UI, adapters, Room, resources, the app or Samsung integration. It cannot catch issue #1's missing HomeViewModel import. Green Core CI is not an Android build pass or phone-test pass.

Actions are pinned to commit SHAs. The job uses read-only repository permissions, no persisted checkout credentials and no secrets. Fork PRs can run the same check subject to GitHub's normal approval settings. Only test reports are uploaded for seven days; no APK, AAR, health data or dump is published.

## Full Android verification remains local

The current app directly references proprietary Samsung SDK classes and requires the existing local `app/libs/samsung-health-data-api.aar` to compile. The repository does not provide/download it. CI does not request the file, a download URL or Samsung credentials. On an already configured development machine run:

```bash
./gradlew test
./gradlew assembleDebug
```

Record actual results in the PR. If local dependencies/toolchains are unavailable, record the blocker and leave full Android verification pending. Instrumentation, actual Samsung imports and phone checks are separate. An SDK-free full app variant could be a later explicit task; this change does not remove Samsung functionality or introduce fake SDK classes.

## Main protection

Inspect a real Actions run first. Once `Core domain unit tests` reliably passes, the owner can require PRs and that status check in a main ruleset. Label it as core-only, not full Android verification. Do not require another person's approval on a solo repository unless a reviewer is available. This PR changes no protection rules, credentials or merge settings.

## License

A public repository is not itself an open-source license. The owner still needs to select a suitable license; this setup does not choose one.
