# ADR-0003: Trusted JVM extension artifacts

- Status: Accepted
- Date: 2026-08-02

## Context

Aniyomi extensions are commonly distributed as Android APKs. Android bytecode,
resources, and framework behavior cannot be made universally executable on a
desktop JVM, while loaded extensions execute code with the application's user
permissions.

## Decision

Use JVM JARs containing `META-INF/extension.json` as the production format.
Validate metadata, package identity, compatible versions, source-ID uniqueness,
archive paths, filesystem type, and trust before class loading. Install and
update atomically; preserve the previous trusted artifact on failure and close
discarded classloaders. APK conversion remains a best-effort migration tool.

## Consequences

Source-built/preconverted JARs are reliable and security failures are visible.
An arbitrary Android APK is not promised to work. Installed-fleet results are
time-stamped observations because provider websites change independently.
