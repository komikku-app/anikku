# libmpv JNA binding and renderer contract

The macOS player binds libmpv directly in `MPVLib.kt`. This document is the
maintenance contract for ABI mappings, native memory, threads, and shutdown.

## Initialization

1. Set the JVM default locale and C `LC_ALL`/`LC_NUMERIC` to `C` before loading
   libmpv.
2. Resolve the packaged `libmpv.2.dylib`, then Homebrew/MacPorts ABI 2 or ABI 1,
   then JNA's normal lookup.
3. Call `mpv_create`, set options including `vo=libmpv` and
   `hwdec=auto-copy-safe`, then call `mpv_initialize`.
4. Register observed properties and start `MPVEventLoop`.
5. Create `MPVSoftwareRenderer` before loading media.

A failure sets the player to `ERROR`. Do not add simulated playback or progress
as a fallback.

## ABI rules

- C functions returning pointers are declared `Pointer?`; test both null and
  native address zero.
- `mpv_command` receives a null-terminated `Array<String?>`.
- Property buffers must match the requested mpv format exactly: 64-bit integer,
  double, flag/int, or pointer.
- `mpv_event` uses offsets 0/4/8/16 for event ID, error, reply userdata, and
  data pointer on the supported 64-bit targets.
- Event payload memory belongs to libmpv only until the next
  `mpv_wait_event`. `MPVEvent` must synchronously copy every value exposed to
  asynchronous Flow consumers.
- JNA callbacks are stored in fields for their entire native registration.

When adding a binding, verify the declaration against mpv's `client.h` or
`render.h`, add an ABI-focused test, and exercise repeated create/destroy.

## Render path

`MPVSoftwareRenderer` creates a software render context and supplies BGR0
pointer, size, scalar stride, and non-blocking target-time parameters. Native
buffers, parameter memory, and the output image are reused. Eight padded rows
protect against legal SIMD over-write behavior observed in native clearing.

The renderer has two locks:

- the state lock snapshots dimensions, buffers, and parameter memory;
- the native-render lock serializes `mpv_render_context_render` with context
  disposal.

`inFlightSnapshot` remains strongly reachable until the native call returns.
Do not replace it with local-only references: JIT/GC reclamation of JNA `Memory`
while libmpv dereferences it causes native heap corruption.

`MPVVideoSurface` renders off the Compose UI thread at an approximately 30 ms
interval, converts the reusable `BufferedImage` to `ImageBitmap`, and displays
with aspect-fit scaling. `hwdec=auto-copy-safe` may decode in hardware, but
frames always arrive in system memory for this output path.

## Event and ownership threading

`MPVEventLoop` polls on `Dispatchers.IO` with a 50 ms timeout and bounded
failure backoff. Its flows are lossy/bounded by design so a stalled UI cannot
block the native event thread. Player state changes are correlated with the
owned playlist entry so a stale `END_FILE` cannot fail a replacement load.

Shutdown order is strict:

1. stop/cancel media-resolution and torrent work;
2. stop and join the event loop so it has left `mpv_wait_event`;
3. dispose the render context and unregister its callback;
4. destroy the mpv handle;
5. clear Kotlin/Compose references.

Never destroy a handle while its event loop or renderer can still enter native
code.

## Required verification

```bash
./gradlew test --tests MPVAbiTest --tests MPVEventLoopTest
./gradlew test --tests MPVRenderExperiment --tests MPVPlaybackTest
./gradlew performancePlaybackTest
./gradlew test --tests StreamingEndToEndTest
```

The renderer experiment covers visible pixels, resize, concurrent
render/dispose, callback lifetime, and repeated initialization. The performance
test generates a high-bitrate fixture and checks playback advance, multiple
seeks, and bounded heap growth.
