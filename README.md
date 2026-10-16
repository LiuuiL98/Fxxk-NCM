# Fxxk-NCM

A modern Android NCM audio converter built from a clean architecture with Kotlin and Jetpack Compose.

> This repository is being bootstrapped. The first milestone is a buildable Android app with Storage Access Framework input/output selection and an independently testable NCM decoding core. NCM conversion must be validated against real files before being considered production-ready.

## Goals

- Phone-first, adaptive UI inspired by Android 17 and Material 3 Expressive.
- Choose one or more `.ncm` files and a destination folder through Android's system file picker.
- Keep decoding logic independent from the UI and use streaming I/O to avoid loading entire audio files into memory.
- Provide GitHub Actions CI to build a debug APK and upload it as an artifact.
- Keep file access scoped; no broad storage permission is requested.

## Build

Open this repository in Android Studio with JDK 17, or run `./gradlew assembleDebug` after the Gradle wrapper has been generated. GitHub Actions uses the Gradle setup action to build the project.

## Current status

The first commit establishes the project foundation. Real-world NCM sample testing, metadata/tag preservation, cancellation, collision policies and release signing are follow-up tasks. Do not treat a CI build alone as proof that all NCM variants decode correctly.
