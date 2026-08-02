# ADR-0001: Isolated Compose Desktop module

- Status: Accepted
- Date: 2026-08-02

## Context

The Android application depends on Activities, Services, WorkManager, Android
storage, and Android UI artifacts. Directly making the entire repository
multiplatform would couple the desktop port to a large, high-risk rewrite.

## Decision

Keep macOS as an independent Gradle build using Compose Desktop and JDK 17.
Consume portable source API/core artifacts as JVM JARs and reproduce required
domain contracts with focused desktop adapters. Use Koin for application
composition while retaining a narrow Injekt bridge for extension compatibility.

## Consequences

Android behavior remains isolated. Desktop code can package with jpackage and
use JVM/native libraries directly. Shared-model changes require refreshing the
two checked-in JVM JARs, and compatibility interfaces must stay intentionally
small and tested.
