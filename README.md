# Gogether Android MVP

Gogether is a small, friendly Android app for discovering and organizing offline interest-based activities. This repository contains the first demonstration skeleton for the CSI301 five-person team.

## Stack and requirements

- Kotlin, Jetpack Compose, Material 3
- MVVM with repository interfaces and a shared in-memory fake repository
- Navigation Compose, Coroutines and StateFlow
- Single `app` module, Gradle Kotlin DSL, min SDK 26
- Android Studio with the SDK configured by a local (untracked) `local.properties`

## Run

Open the existing project in Android Studio, let Gradle sync, select an emulator/device, and run `app`. From a terminal:

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```

The launcher name is **Gogether**. Package, namespace, and application ID are `com.gogether.app`.

## Structure

- `core/model`: domain models and status enums
- `core/data`: repository contract, fake implementation, validation, app container
- `core/ui`: shared ViewModel and Compose components
- `navigation`: app routes and bottom navigation
- `feature/*`: auth, explore, event, registration, coordination, check-in, profile

## Demo mode limitations

All data is process-local and resets when the app process restarts. Login/register, check-in and notifications are demonstrations only. There is no secure authentication, backend persistence, concurrency guarantee, QR verification, payment, chat, or production moderation. Do not enter real personal data.

## Five-person collaboration

Use small feature branches and pull requests, keep domain changes backward-compatible, run unit tests before review, and avoid committing SDK paths, credentials or generated output. Ownership boundaries are documented in `docs/MODULE_OWNERSHIP.md`.

## Firebase integration plan

The fake repository is deliberately behind an interface. A later implementation can add Firebase Authentication, Firestore transactions for capacity/waitlist consistency, Cloud Functions for trusted promotion/notifications, Security Rules, and emulator-backed integration tests without replacing Compose screens.

See `docs/ARCHITECTURE.md`, `docs/DEVELOPMENT_PLAN.md`, and `docs/TESTING.md` for details.
