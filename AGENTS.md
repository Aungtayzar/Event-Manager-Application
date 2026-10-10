# Repository Guidelines

## Project Structure & Module Organization

This is a single-module Android application. App code and resources live under `app/src/main`; Kotlin classes use the `com.example.eventmanagerapplication` package, while layouts and values are in `res/layout` and `res/values`. Local JVM tests belong in `app/src/test`, and device or emulator tests belong in `app/src/androidTest`. Gradle configuration is in the root and `app` `build.gradle.kts` files, with dependency versions centralized in `gradle/libs.versions.toml`. Do not edit generated `build/` output or machine-specific `local.properties`.

## Build, Test, and Development Commands

Run commands from the repository root using the Gradle wrapper:

- `./gradlew assembleDebug` (Windows: `gradlew.bat assembleDebug`) builds a debug APK.
- `./gradlew test` runs local JVM unit tests.
- `./gradlew connectedAndroidTest` runs instrumentation tests on a connected device or emulator.
- `./gradlew installDebug` builds and installs the debug app on a connected device.

Android SDK and a compatible JDK must be configured locally. Open the root directory in Android Studio to run or debug with its emulator tools.

## Coding Style & Naming Conventions

Use Kotlin conventions: four-space indentation, `UpperCamelCase` for classes, and `lowerCamelCase` for functions, properties, and variables. Keep package names lowercase and resources in Android `snake_case` (for example, `activity_main.xml`). Follow the existing XML layout and Kotlin style, use descriptive names, and keep changes focused. No formatter or linter is currently configured; rely on Android Studio's Kotlin formatting.

## Testing Guidelines

Use JUnit 4 for local tests and AndroidX JUnit/Espresso for instrumentation tests. Name test classes `*Test` and test methods to describe behavior (for example, `loadsEventList`). Run `./gradlew test` for logic changes; run `./gradlew connectedAndroidTest` when behavior depends on Android UI or device APIs. Add tests alongside the corresponding source set and cover changed behavior.

## Commit & Pull Request Guidelines

Git metadata and commit history are not available in this checkout, so established commit conventions cannot be verified. Use short, imperative commit subjects, optionally scoped (for example, `ui: add event list screen`). For pull requests, describe the user-visible change, summarize implementation and validation commands, link any related issue, and include screenshots for UI changes. Mention platform or SDK assumptions when relevant.

## Security & Configuration Tips

Keep SDK paths and local machine settings in ignored `local.properties`. Do not commit signing credentials, API keys, or other secrets; use local configuration or an approved secrets mechanism.
