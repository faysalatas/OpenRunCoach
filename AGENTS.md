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

## Git

- Never commit secrets or API keys.
- Keep changes focused.
