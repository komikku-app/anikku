package app.anikku.macos.platform

import com.sun.jna.Callback
import com.sun.jna.CallbackReference
import com.sun.jna.Memory
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * macOS media keys + Now Playing integration (MediaPlayer framework).
 *
 * Uses the PUBLIC MediaPlayer framework via JNA/ObjC — no private frameworks,
 * no accessibility permissions, no event taps:
 *
 * - **Receive commands** via `MPRemoteCommandCenter` (play/pause/toggle/next/
 *   previous/seek). The system routes keyboard media keys, AirPods remote and
 *   Control Center / lock screen buttons to the app through this mechanism
 *   whenever the app is the active Now Playing application.
 * - **Publish state** via `MPNowPlayingInfoCenter.nowPlayingInfo` so the
 *   anime/episode shows up in Control Center's Now Playing widget with a live
 *   position and correct play/pause state.
 *
 * Mirrors the [MacOSDockManager] degradation pattern: every native call is
 * guarded, and on any failure the handler silently no-ops ([isSupported] =
 * false) instead of crashing.
 *
 * ## Usage
 *
 * ```kotlin
 * // While the player is open:
 * MacOSNowPlayingHandler.onTogglePlayPause = { playerViewModel.togglePause() }
 * MacOSNowPlayingHandler.updateNowPlaying(title = "Frieren — Ep 3", artist = "Frieren", ...)
 * // On player close:
 * MacOSNowPlayingHandler.clearNowPlaying()
 * ```
 */
object MacOSNowPlayingHandler {

    /** Playback command callbacks — install/restore from the player's lifecycle. */
    var onPlay: () -> Unit = {}
    var onPause: () -> Unit = {}
    var onTogglePlayPause: () -> Unit = {}
    var onNextTrack: () -> Unit = {}
    var onPreviousTrack: () -> Unit = {}
    var onSeekTo: (Double) -> Unit = {}

    /** Whether the MediaPlayer bridge is available. */
    val isSupported: Boolean get() = available

    private var available = false

    /** Retain the framework library so JNA never dlcloses it while classes/symbols are live. */
    private var mediaPlayerLib: NativeLibrary? = null

    private var commandCenter: Pointer? = null
    private var nowPlayingInfoCenter: Pointer? = null

    // Retained so JNA trampolines and block memory outlive registration.
    private val retainedCallbacks = mutableListOf<CommandHandler>()
    private val retainedBlocks = mutableListOf<Memory>()
    /** (command, target) pairs for removeTarget: on shutdown. */
    private val registeredTargets = mutableListOf<Pair<Pointer, Pointer>>()

    // MediaPlayer framework symbols.
    private var mediaItemTitle: Pointer? = null
    private var mediaItemArtist: Pointer? = null
    private var mediaItemDuration: Pointer? = null
    private var mediaItemArtwork: Pointer? = null
    private var nowPlayingElapsed: Pointer? = null
    private var nowPlayingRate: Pointer? = null

    /** JNA block invoke signature: MPRemoteCommandHandlerStatus (^)(MPRemoteCommandEvent *). */
    private fun interface CommandHandler : Callback {
        fun invoke(block: Pointer, event: Pointer): Long
    }

    init {
        initialize()
    }

    private fun initialize() {
        try {
            val osName = System.getProperty("os.name").lowercase()
            if (!osName.contains("mac")) {
                logger.warn { "Now Playing handler not available on: $osName" }
                return
            }
            ObjC.objc_getClass("NSObject") // verify the ObjC bridge works
            // dlopen MediaPlayer.framework — registers its ObjC classes and
            // exports the MPMediaItemProperty* / MPNowPlayingInfoProperty* globals.
            mediaPlayerLib = runCatching { NativeLibrary.getInstance("MediaPlayer") }
                .getOrElse {
                    NativeLibrary.getInstance("/System/Library/Frameworks/MediaPlayer.framework/MediaPlayer")
                }
            mediaItemTitle = symbol("MPMediaItemPropertyTitle")
            mediaItemArtist = symbol("MPMediaItemPropertyArtist")
            mediaItemDuration = symbol("MPMediaItemPropertyPlaybackDuration")
            mediaItemArtwork = symbol("MPMediaItemPropertyArtwork")
            nowPlayingElapsed = symbol("MPNowPlayingInfoPropertyElapsedPlaybackTime")
            nowPlayingRate = symbol("MPNowPlayingInfoPropertyPlaybackRate")
            available = true
            logger.debug { "Now Playing handler initialized" }
        } catch (e: Throwable) {
            available = false
            logger.warn(e) { "MediaPlayer bridge unavailable, Now Playing integration disabled" }
        }
    }

    /**
     * Resolve an exported CFStringRef global. The symbol's storage holds the
     * NSString pointer (the variable IS the pointer), so dereference the
     * returned address once — passing the storage address itself would hand
     * the dictionary a garbage object pointer.
     */
    private fun symbol(name: String): Pointer {
        val lib = mediaPlayerLib ?: throw UnsatisfiedLinkError("MediaPlayer not loaded")
        val candidates = listOf(name, "_$name")
        for (candidate in candidates) {
            runCatching { return lib.getGlobalVariableAddress(candidate).getPointer(0) }
        }
        throw UnsatisfiedLinkError("MediaPlayer symbol not found: $name")
    }

    /**
     * Register the media-key command handlers. Idempotent — call once when the
     * player opens. Use [unregisterCommands] when the player closes.
     */
    fun registerCommands() {
        if (!available) return
        try {
            val center = ObjC.objc_msgSend(ObjC.objc_getClass("MPRemoteCommandCenter"), sel("sharedCommandCenter"))
            if (center == null || Pointer.nativeValue(center) == 0L) return
            commandCenter = center

            addCommand(center, "playCommand") { onPlay() }
            addCommand(center, "pauseCommand") { onPause() }
            addCommand(center, "togglePlayPauseCommand") { onTogglePlayPause() }
            addCommand(center, "nextTrackCommand") { onNextTrack() }
            addCommand(center, "previousTrackCommand") { onPreviousTrack() }
            addCommandWithEvent(center, "changePlaybackPositionCommand") { event ->
                val time = if (event != null && Pointer.nativeValue(event) != 0L) {
                    ObjC.objc_msgSend_double(event, sel("positionTime"))
                } else {
                    0.0
                }
                onSeekTo(time)
            }
            logger.debug { "Media key commands registered" }
        } catch (e: Throwable) {
            logger.warn(e) { "Failed to register media key commands" }
        }
    }

    /** Remove all registered command targets. */
    fun unregisterCommands() {
        val center = commandCenter ?: return
        try {
            registeredTargets.toList().forEach { (command, target) ->
                runCatching { ObjC.objc_msgSend_void(command, sel("removeTarget:"), target) }
            }
            registeredTargets.clear()
            retainedCallbacks.clear()
            retainedBlocks.clear()
            commandCenter = null
            logger.debug { "Media key commands unregistered" }
        } catch (e: Throwable) {
            logger.warn(e) { "Failed to unregister media key commands" }
        }
    }

    /**
     * Publish playback metadata to Now Playing. Pass null/blank [title] to
     * leave the widget untouched. [elapsedSeconds] is clamped to the duration.
     */
    fun updateNowPlaying(
        title: String?,
        artist: String?,
        durationSeconds: Double,
        elapsedSeconds: Double,
        playing: Boolean,
        rate: Double,
        artworkPath: String? = null,
    ) {
        if (!available || title.isNullOrBlank()) return
        try {
            val center = nowPlayingInfoCenter()
            val elapsed = elapsedSeconds.coerceIn(0.0, durationSeconds.coerceAtLeast(0.0))
            val pairs = mutableListOf<Pair<Pointer, Pointer>>(
                mediaItemTitle!! to nsString(title),
                mediaItemArtist!! to nsString(artist ?: ""),
                mediaItemDuration!! to nsNumber(durationSeconds.coerceAtLeast(0.0)),
                nowPlayingElapsed!! to nsNumber(elapsed),
                nowPlayingRate!! to nsNumber(if (playing) rate.coerceAtLeast(0.0) else 0.0),
            )
            artworkPath?.takeIf { java.io.File(it).isFile }?.let { path ->
                nsImage(path)?.let { image ->
                    val artwork = mpArtwork(image)
                    if (artwork != null && Pointer.nativeValue(artwork) != 0L) {
                        pairs += mediaItemArtwork!! to artwork
                    }
                }
            }
            val dict = buildDictionary(*pairs.toTypedArray())
            ObjC.objc_msgSend_void(center, sel("setNowPlayingInfo:"), dict)
        } catch (e: Throwable) {
            logger.warn(e) { "Failed to update Now Playing info" }
        }
    }

    /**
     * Load an NSImage from a file path, or null.
     *
     * NOTE: `initWithContentsOfFile:` takes an NSString * (id), NOT a C
     * string. Passing a `const char*` (what objc_msgSend_str marshals) makes
     * AppKit interpret the path bytes as an ObjC object — a NULL/garbage isa
     * dereference that crashes the JVM (seen in production as a SIGSEGV inside
     * -[NSImage initWithContentsOfFile:] on the AWT thread). Always build a
     * real NSString first.
     */
    private fun nsImage(path: String): Pointer? {
        val cls = ObjC.objc_getClass("NSImage")
        val alloced = ObjC.objc_msgSend(cls, sel("alloc"))
        if (Pointer.nativeValue(alloced) == 0L) return null
        val pathString = nsString(path)
        if (Pointer.nativeValue(pathString) == 0L) return null
        return ObjC.objc_msgSend(alloced, sel("initWithContentsOfFile:"), pathString)
    }

    /**
     * MPMediaItemArtwork from an NSImage. `initWithImage:` is deprecated since
     * macOS 13 but still functional — the modern block-based initializer is
     * not reachable through the JNA ObjC bridge (struct-by-value CGSize).
     */
    private fun mpArtwork(image: Pointer): Pointer? {
        val cls = ObjC.objc_getClass("MPMediaItemArtwork")
        val alloced = ObjC.objc_msgSend(cls, sel("alloc"))
        if (Pointer.nativeValue(alloced) == 0L) return null
        return ObjC.objc_msgSend(alloced, sel("initWithImage:"), image)
    }

    /** Remove the app from Now Playing (call when the player closes). */
    fun clearNowPlaying() {
        if (!available) return
        try {
            val center = nowPlayingInfoCenter()
            ObjC.objc_msgSend_void(center, sel("setNowPlayingInfo:"), Pointer.NULL)
        } catch (e: Throwable) {
            logger.warn(e) { "Failed to clear Now Playing info" }
        }
    }

    // -----------------------------------------------------------------------
    // Internal
    // -----------------------------------------------------------------------

    private fun nowPlayingInfoCenter(): Pointer {
        val cached = nowPlayingInfoCenter
        if (cached != null && Pointer.nativeValue(cached) != 0L) return cached
        val center = ObjC.objc_msgSend(ObjC.objc_getClass("MPNowPlayingInfoCenter"), sel("defaultCenter"))
        nowPlayingInfoCenter = center
        return center
    }

    private fun addCommand(center: Pointer, commandName: String, action: () -> Unit) {
        val command = ObjC.objc_msgSend(center, sel(commandName))
        if (command == null || Pointer.nativeValue(command) == 0L) return
        val callback = CommandHandler { _, _ ->
            action()
            0L // MPRemoteCommandHandlerStatusSuccess
        }
        register(command, callback)
    }

    private fun addCommandWithEvent(center: Pointer, commandName: String, action: (Pointer) -> Unit) {
        val command = ObjC.objc_msgSend(center, sel(commandName))
        if (command == null || Pointer.nativeValue(command) == 0L) return
        val callback = CommandHandler { _, event ->
            action(event)
            0L
        }
        register(command, callback)
    }

    private fun register(command: Pointer, callback: CommandHandler) {
        retainedCallbacks += callback
        val block = ObjC.createBlock(CallbackReference.getFunctionPointer(callback))
        retainedBlocks += block
        val target = ObjC.objc_msgSend(command, sel("addTargetWithHandler:"), block)
        if (target != null && Pointer.nativeValue(target) != 0L) {
            registeredTargets += command to target
        }
        ObjC.objc_msgSend_void(command, sel("setEnabled:"), 1L)
    }

    private fun nsString(value: String): Pointer {
        val cls = ObjC.objc_getClass("NSString")
        return ObjC.objc_msgSend_str(cls, sel("stringWithUTF8String:"), value)
    }

    private fun nsNumber(value: Double): Pointer {
        val cls = ObjC.objc_getClass("NSNumber")
        return ObjC.objc_msgSend(cls, sel("numberWithDouble:"), value)
    }

    private fun buildDictionary(vararg pairs: Pair<Pointer, Pointer>): Pointer {
        val cls = ObjC.objc_getClass("NSDictionary")
        val objects = Array(pairs.size) { pairs[it].second }
        val keys = Array(pairs.size) { pairs[it].first }
        return ObjC.objc_msgSend(cls, sel("dictionaryWithObjects:forKeys:count:"), objects, keys, pairs.size.toLong())
    }

    private fun sel(name: String): Pointer = ObjC.sel_registerName(name)
}
