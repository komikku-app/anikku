# ADR-0002: Native libmpv through JNA and the Render API

- Status: Accepted
- Date: 2026-08-02

## Context

The player needs mpv-compatible properties, commands, tracks, filters, and
events. Passing an AWT/native window ID is unreliable on modern macOS and does
not fit Compose rendering.

## Decision

Bind libmpv's C API with JNA, configure `vo=libmpv`, and use
`MPV_RENDER_API_TYPE_SW`. Frames are copied from reusable native BGR0 buffers to
a reusable `BufferedImage` and displayed by Compose. Use
`hwdec=auto-copy-safe`: decoded frames may use safe hardware acceleration but
are copied into system memory for the software Render API.

## Consequences

The UI remains pure Compose and the Android-style mpv control surface maps
closely to desktop. Frame transfer has a CPU/memory-bandwidth cost, so buffers
are reused and performance/heap regression tests are required. Native pointer,
callback, event-payload, and disposal ownership are explicit; see
[../MPV-JNA.md](../MPV-JNA.md).
