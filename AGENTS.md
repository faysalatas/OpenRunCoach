# OpenRunCoach

## Project

OpenRunCoach is a native Android adaptive running coach.

## Technology

- Kotlin
- Jetpack Compose
- Health Connect
- Room
- Kotlin Coroutines and Flow
- Hilt
- DataStore
- Gradle Kotlin DSL

## Architecture

- Use MVVM.
- Separate UI, domain, and data layers.
- Do not put business logic inside Composables.
- Keep the coaching engine independent from Android UI code.
- Use repositories for data access.
- Provider-specific health models must not leak into domain, coaching, or general UI layers. Convert provider data into provider-independent domain models; provider diagnostics may show source details.
- Workout logic must remain provider-independent.
- Workout prescriptions must support structured blocks and segments; do not reduce every workout to one total duration.
- New workout types must use the shared structured workout model.
- Base training plans must be deterministic and explainable.
- Low profile confidence must produce conservative prescriptions.
- A training goal's distance does not make every workout that distance.
- Hard workouts require explicit eligibility based on available training evidence.
- Generated plan workouts must use the shared structured workout model.

## Health data

- Use Android Health Connect.
- Do not use proprietary Samsung APIs unless explicitly requested.
- Request only permissions that are actually required.
- Health data must remain local unless explicitly changed later.
- Treat Samsung Health as one possible Health Connect provider.

## Development

- Prefer simple implementations.
- Avoid unnecessary dependencies.
- Add unit tests for coaching and business logic.
- Run tests after meaningful changes.
- Run a Gradle build after meaningful changes.
- Do not silently change architecture decisions.
- Do not implement features that were not requested.
- Whenever a meaningful feature, architecture change, new metric, new workflow, or roadmap milestone is implemented, update README.md in the same change if documentation would otherwise become outdated.

## Git

- Never commit secrets or API keys.
- Keep changes focused.
- Read the linked issue and its acceptance criteria before implementation; verify the current code rather than assuming a reported issue is still present.
- Use a focused branch and pull request per task. Link the issue and preserve unrelated local changes. Do not merge into main unless explicitly requested.
- Record exact verification commands, actual results and blockers. Never claim a check passed if it was not run successfully.
- Distinguish implemented code, passing automated checks and device verification. Mark device checks not required, pending, passed or failed with evidence.
- Keep original prescriptions, effective adjustments and actual performance distinct when changing coaching behavior.
- Do not close an issue until its acceptance criteria are satisfied; a partial implementation needs an explicit remaining-work list.
- Never commit signing keys, tokens, SDK binaries, private health data or diagnostic dumps. CI must not require the owner to host or supply a proprietary SDK; SDK-independent checks and local Android verification are documented in docs/repository-workflow.md.
- Use the PR template and update user-facing documentation when behavior or setup changes.

## UI / Design

For all UI work, read `docs/UI_RULEBOOK.md`.

Use `docs/ui/openruncoach-ui-target.png` as the approved visual reference for the OpenRunCoach design language.

The rulebook is authoritative for colors, spacing, typography, component style, information hierarchy, and UI consistency.
