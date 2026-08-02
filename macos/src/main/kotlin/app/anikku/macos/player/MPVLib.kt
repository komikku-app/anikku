package app.anikku.macos.player

import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import com.sun.jna.ptr.LongByReference
import com.sun.jna.ptr.PointerByReference
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File

private val logger = KotlinLogging.logger {}

/**
 * JNA interface mapping to libmpv's C API.
 *
 * This mirrors the API surface used by the Android app (is.xyz.mpv.MPVLib)
 * so that the player subsystem can use the same patterns.
 *
 * **Usage:** Call [MPVLib.initialize] before any other methods.
 * If libmpv cannot be loaded, [isAvailable] returns false and all operations
 * become no-ops with logged warnings.
 *
 * ## Supported property/option mapping
 *
 * | Android MPVLib call | Equivalent JNA call |
 * |---|---|
 * | `MPVLib.create()` | `{@code create()}` |
 * | `MPVLib.setOptionString(k, v)` | `{@code setOptionString(handle, k, v)}` |
 * | `MPVLib.setPropertyString(k, v)` | `{@code setPropertyString(handle, k, v)}` |
 * | `MPVLib.command(args)` | `{@code command(handle, args)}` |
 * | `MPVLib.event()` | `{@code waitEvent(handle, timeout)}` |
 *
 * @see <a href="https://mpv.io/manual/stable/#c-api">mpv C API documentation</a>
 */
object MPVLib {

    private var isInitialized = false

    /** Whether a previous initialization attempt permanently failed. Prevents retry loops. */
    private var initFailed = false

    /** Whether libmpv was successfully loaded and is available for use. */
    val isAvailable: Boolean get() = isInitialized && !initFailed

    /**
     * Attempt to load libmpv from standard locations.
     *
     * Search order:
     * 1. Bundle path: `Anikku.app/Contents/Frameworks/libmpv.1.dylib`
     * 2. Homebrew: `/opt/homebrew/lib/libmpv.1.dylib` (Apple Silicon)
     * 3. Homebrew (Intel): `/usr/local/lib/libmpv.1.dylib`
     * 4. MacPorts: `/opt/local/lib/libmpv.1.dylib`
     * 5. System `java.library.path`
     * 6. Default JNA lookup (DYLD_LIBRARY_PATH, etc.)
     */
    /**
     * Attempt to load libmpv from standard locations.
     *
     * @return true if libmpv was successfully loaded and is available.
     */
    fun initialize(): Boolean {
        if (isInitialized) return true
        // Permanent failure guard — prevents retry loop when checkAvailable() calls
        // initialize() repeatedly after mpv_create() or library loading fails.
        if (initFailed) {
            logger.warn { "MPV initialization previously failed — skipping retry" }
            return false
        }

        // mpv requires LC_NUMERIC=C — non-C locales break its config parsing.
        // We must set the locale BEFORE loading libmpv via dlopen, because
        // mpv checks locale in its library constructor (runs during dlopen).
        // JNA's setlocale via NativeLibrary works, but we also call Java's
        // Locale.setDefault() as a supplementary fallback.
        java.util.Locale.setDefault(java.util.Locale.US)
        forceCLocale()

        val libraryPaths = listOfNotNull(
            // Bundle path (Phase 6.1)
            findBundleLibrary(),
            // Homebrew Apple Silicon (mpv ships libmpv.2.dylib as of 0.41.0+)
            "/opt/homebrew/lib/libmpv.2.dylib",
            "/opt/homebrew/lib/libmpv.1.dylib",
            // Homebrew Intel
            "/usr/local/lib/libmpv.2.dylib",
            "/usr/local/lib/libmpv.1.dylib",
            // MacPorts
            "/opt/local/lib/libmpv.2.dylib",
            "/opt/local/lib/libmpv.1.dylib",
        ).filter { File(it).isFile }

        val libPath = libraryPaths.firstOrNull()

        if (libPath == null) {
            logger.warn { "libmpv not found in standard paths. Attempting JNA default lookup..." }
        }

        try {
            val lib = if (libPath != null) {
                logger.info { "Loading libmpv from: $libPath" }
                Native.load(libPath, MPVNatives::class.java) as MPVNatives
            } else {
                logger.info { "Loading libmpv via JNA default lookup" }
                Native.load("mpv", MPVNatives::class.java) as MPVNatives
            }
            instance = lib
            isInitialized = true

            // Verify mpv_create() works
            val testHandle = lib.mpv_create()
            if (testHandle == null || Pointer.nativeValue(testHandle) == 0L) {
                logger.error { "mpv_create() returned null — libmpv loaded but cannot create handle. " +
                    "This may be caused by a locale issue (LC_NUMERIC not set to C). Checking..." }
                isInitialized = false
                instance = null
                initFailed = true
                return false
            }
            lib.mpv_destroy(testHandle)

            logger.info { "libmpv loaded successfully (v${getVersion()})" }
            return true
        } catch (e: UnsatisfiedLinkError) {
            logger.error(e) { "Failed to load libmpv — video playback unavailable" }
            logger.warn { "Install mpv via: brew install mpv" }
            logger.warn { "Or bundle libmpv in Anikku.app/Contents/Frameworks/" }
            instance = null
            isInitialized = false
            initFailed = true
            return false
        }
    }

    /**
     * Force LC_NUMERIC to "C" before mpv initialization.
     *
     * mpv requires the C locale for LC_NUMERIC because some locales use
     * commas instead of dots as decimal separators, which breaks mpv's
     * config file and option parsing. mpv will refuse to initialize
     * with the error: "Non-C locale detected. This is not supported."
     *
     * Strategy:
     * 1. First try Java's Locale.setDefault() (affects JVM-wide locale)
     * 2. Then use JNA to call the C library's setlocale() directly
     * 3. Try LC_ALL first, then LC_NUMERIC specifically
     */
    private fun forceCLocale() {
        var success = false

        try {
            val libc = NativeLibrary.getInstance("c")
            val setlocale = libc.getFunction("setlocale")
            // LC_ALL = 0 (all locale categories) — most reliable
            val result = setlocale.invoke(arrayOf(0, "C"))
            val resultStr = if (result is Pointer && Pointer.nativeValue(result) != 0L) {
                result.getString(0)
            } else {
                result?.toString() ?: "null"
            }
            logger.debug { "setlocale(LC_ALL, \"C\") returned: $resultStr" }
            success = resultStr.contains("C")
        } catch (e: Throwable) {
            logger.warn(e) { "JNA setlocale(LC_ALL) failed — trying LC_NUMERIC..." }
        }

        if (!success) {
            try {
                val libc = NativeLibrary.getInstance("System")
                val setlocale = libc.getFunction("setlocale")
                setlocale.invoke(arrayOf(1, "C"))
                logger.debug { "setlocale(LC_NUMERIC, \"C\") via libSystem succeeded" }
                success = true
            } catch (e: Throwable) {
                logger.warn(e) { "All setlocale attempts failed — mpv may not initialize" }
            }
        }
    }

    private fun findBundleLibrary(): String? {
        val packagedResources = System.getProperty("compose.application.resources.dir")
            ?.takeIf { it.isNotBlank() }
            ?.let(::File)
        val bundleCandidates = listOfNotNull(
            packagedResources?.resolve("libmpv.2.dylib")?.path,
            packagedResources?.resolve("libmpv.1.dylib")?.path,
            // Compatibility with older package layouts and local launches.
            "../Resources/libmpv.2.dylib",
            "../Resources/libmpv.1.dylib",
            "../lib/libmpv.2.dylib",
            "../lib/libmpv.1.dylib",
            "../Frameworks/libmpv.2.dylib",
            "../../Frameworks/libmpv.2.dylib",
            "../../Frameworks/libmpv.1.dylib",
        )
        return bundleCandidates.firstOrNull { File(it).isFile }
    }

    // -------------------------------------------------------------------------
    // Delegated native calls
    // -------------------------------------------------------------------------

    private var instance: MPVNatives? = null

    private fun checkAvailable(): MPVNatives {
        if (!isInitialized && !initFailed) initialize()
        return instance ?: error("libmpv not available. Install via: brew install mpv")
    }

    /** Create a new mpv handle. */
    /** Create a new mpv handle. Returns null on failure. */
    fun create(): Pointer? {
        return try {
            val h = checkAvailable().mpv_create()
            if (h == null || Pointer.nativeValue(h) == 0L) {
                logger.error { "mpv_create() returned null — possible locale/initialization issue" }
                null
            } else {
                h
            }
        } catch (e: Exception) {
            logger.error(e) { "mpv_create() threw exception" }
            null
        }
    }

    /** Initialize an mpv handle after setting options. Returns error code, or null if handle is invalid. */
    fun initialize(handle: Pointer?): Int? {
        if (handle == null) {
            logger.warn { "mpv_initialize called with null handle" }
            return null
        }
        return try {
            checkAvailable().mpv_initialize(handle)
        } catch (e: Exception) {
            logger.error(e) { "mpv_initialize threw exception" }
            null
        }
    }

    /** Destroy an mpv handle and free resources. */
    fun destroy(handle: Pointer?) {
        if (handle == null) return
        try {
            checkAvailable().mpv_destroy(handle)
        } catch (_: Exception) {
            // Already destroyed — safe to ignore
        }
    }

    /** Set a string option before mpv_initialize. Returns null if handle is invalid. */
    fun setOptionString(handle: Pointer?, name: String, value: String): Int? {
        if (handle == null) {
            logger.warn { "setOptionString called with null handle: $name=$value" }
            return null
        }
        return try {
            checkAvailable().mpv_set_option_string(handle, name, value)
        } catch (e: Exception) {
            logger.warn(e) { "Failed to set mpv option: $name=$value" }
            null
        }
    }

    /** Set a string property at runtime. Returns null if handle is invalid. */
    fun setPropertyString(handle: Pointer?, name: String, value: String): Int? {
        if (handle == null) return null
        return try {
            checkAvailable().mpv_set_property_string(handle, name, value)
        } catch (e: Exception) {
            null
        }
    }

    /** Set an integer property at runtime. Returns null if handle is invalid. */
    fun setPropertyInt(handle: Pointer?, name: String, value: Int): Int? {
        if (handle == null) return null
        return try {
            checkAvailable().mpv_set_property(handle, name, FORMAT_INT64, LongByReference(value.toLong()).pointer)
        } catch (e: Exception) {
            null
        }
    }

    /** Set a double property at runtime. Returns null if handle is invalid. */
    fun setPropertyDouble(handle: Pointer?, name: String, value: Double): Int? {
        if (handle == null) return null
        return try {
            checkAvailable().mpv_set_property(handle, name, FORMAT_DOUBLE, DoubleArrayHolder(value).pointer)
        } catch (e: Exception) {
            null
        }
    }

    /** Get a string property. Returns null if handle is invalid. */
    fun getPropertyString(handle: Pointer?, name: String): String? {
        if (handle == null) return null
        val ptr = PointerByReference()
        val result = checkAvailable().mpv_get_property(handle, name, FORMAT_STRING, ptr.pointer)
        if (result >= 0 && ptr.value != null) {
            val str = ptr.value.getString(0)
            checkAvailable().mpv_free(ptr.value)
            return str
        }
        return null
    }

    /** Get an integer property. Returns default if handle is invalid. */
    fun getPropertyInt(handle: Pointer?, name: String, default: Int = 0): Int {
        if (handle == null) return default
        val ref = LongByReference()
        val result = checkAvailable().mpv_get_property(handle, name, FORMAT_INT64, ref.pointer)
        return if (result >= 0) ref.value.toInt() else default
    }

    /** Get a double property. Returns default if handle is invalid. */
    fun getPropertyDouble(handle: Pointer?, name: String, default: Double = 0.0): Double {
        if (handle == null) return default
        val holder = DoubleArrayHolder()
        val result = checkAvailable().mpv_get_property(handle, name, FORMAT_DOUBLE, holder.pointer)
        return if (result >= 0) holder.value else default
    }

    /** Get a flag (boolean) property. Returns default if handle is invalid. */
    fun getPropertyFlag(handle: Pointer?, name: String, default: Boolean = false): Boolean {
        if (handle == null) return default
        val ref = LongByReference()
        val result = checkAvailable().mpv_get_property(handle, name, FORMAT_FLAG, ref.pointer)
        return if (result >= 0) ref.value != 0L else default
    }

    /** Send a command to mpv. Returns error code, or -999 if handle is invalid. */
    fun command(handle: Pointer?, vararg args: String): Int {
        if (handle == null) {
            logger.warn { "command called with null handle: ${args.joinToString(" ")}" }
            return -999
        }
        // JNA requires null-terminated array of C strings
        val cArgs = args.map { it.ifEmpty { null } }.toTypedArray()
        return try {
            checkAvailable().mpv_command(handle, cArgs)
        } catch (e: Exception) {
            logger.warn(e) { "mpv_command failed" }
            -999
        }
    }

    /** Observe a property for changes. */
    fun observeProperty(handle: Pointer?, replyUserdata: Long, name: String, format: Int): Int {
        if (handle == null) return -999
        return checkAvailable().mpv_observe_property(handle, replyUserdata, name, format)
    }

    /** Unobserve a property. */
    fun unobserveProperty(handle: Pointer?, replyUserdata: Long): Int {
        if (handle == null) return -999
        return checkAvailable().mpv_unobserve_property(handle, replyUserdata)
    }

    /** Request a property change event. */
    fun requestEvent(handle: Pointer?, event: Int, enable: Boolean): Int {
        if (handle == null) return -999
        return checkAvailable().mpv_request_event(handle, event, if (enable) 1 else 0)
    }

    /** Wait for the next mpv event (blocking). Timeout in seconds (0 = no wait). */
    fun waitEvent(handle: Pointer?, timeout: Double = 0.0): MPVEvent? {
        if (handle == null) return null
        val ptr = checkAvailable().mpv_wait_event(handle, timeout)
        if (ptr == null || ptr == Pointer.NULL) return null
        val event = MPVEvent(ptr)
        return if (event.eventId == MPV_EVENT_NONE) null else event
    }

    /** Get the mpv client name. Returns null if handle is invalid. */
    fun clientName(handle: Pointer?): String {
        if (handle == null) return "unknown"
        return checkAvailable().mpv_client_name(handle)?.getString(0) ?: "unknown"
    }

    /** Get the mpv version number. */
    fun getVersion(): Long = checkAvailable().mpv_client_api_version()

    /** Suspend/resume the main loop (useful during render context operations). */
    fun suspend(handle: Pointer?) { if (handle != null) checkAvailable().mpv_suspend(handle) }
    fun resume(handle: Pointer?) { if (handle != null) checkAvailable().mpv_resume(handle) }

    // -------------------------------------------------------------------------
    // Render API
    // -------------------------------------------------------------------------

    /**
     * Build an array of [mpv_render_param] in native memory.
     * Each pair is (type, dataPointer). The array is terminated with INVALID.
     *
     * **IMPORTANT:** Returns [Memory] (not [Pointer]) so callers MUST hold a
     * reference to prevent Java GC from freeing the native buffer while mpv
     * is still reading it. Storing only as [Pointer] causes "memory corruption
     * of free block" crashes (SIGBUS) when the GC finalizer runs.
     *
     * @param params Variable number of (type, dataPointer) pairs.
     * @return Memory block containing the param array. Caller must retain reference.
     */
    fun buildRenderParams(vararg params: Pair<Int, Pointer?>): Memory {
        val count = params.size + 1 // +1 for INVALID terminator
        val mem = Memory((count * 16).toLong())
        // CRITICAL: JNA Memory is malloc'd (uninitialized garbage).
        // Without clear(), the terminator struct and padding bytes contain
        // random data. mpv reads garbage type IDs → treats them as valid
        // params → dereferences garbage pointers → heap corruption.
        mem.clear()
        for ((i, pair) in params.withIndex()) {
            mem.setInt(i * 16.toLong(), pair.first)
            pair.second?.let { mem.setPointer(i * 16L + 8, it) }
        }
        // Terminator: type=INVALID (0), data=NULL (already zeroed)
        return mem
    }

    /** Create a software render context for the given mpv handle. */
    fun renderContextCreate(mpvHandle: Pointer?): Pointer? {
        if (mpvHandle == null) {
            logger.warn { "renderContextCreate called with null handle" }
            return null
        }
        // Hold apiTypeMem locally to prevent GC of native string before mpv reads it.
        val apiTypeMem = Memory(3L).also { it.setString(0, RENDER_API_TYPE_SW) }
        val params = buildRenderParams(RENDER_PARAM_API_TYPE to apiTypeMem)
        val res = PointerByReference()
        val result = checkAvailable().mpv_render_context_create(res, mpvHandle, params)
        return if (result >= 0) res.value else null
    }

    /** Free a render context. */
    fun renderContextFree(ctx: Pointer?) {
        if (ctx == null) return
        try {
            checkAvailable().mpv_render_context_free(ctx)
        } catch (_: Exception) { }
    }

    /** Render a frame into the provided buffer. */
    fun renderContextRender(ctx: Pointer?, params: Pointer): Int {
        if (ctx == null) return -999
        return checkAvailable().mpv_render_context_render(ctx, params)
    }

    /** Request log messages from mpv at the given minimum level.
     * This is the CORRECT API for receiving MPV_EVENT_LOG_MESSAGE events.
     * Using mpv_request_event(MPV_EVENT_LOG_MESSAGE) alone does NOT work —
     * log messages are managed through this dedicated function. */
    fun requestLogMessages(handle: Pointer?, minLevel: String): Int {
        if (handle == null) return -999
        return try {
            checkAvailable().mpv_request_log_messages(handle, minLevel)
        } catch (e: Exception) {
            logger.warn(e) { "Failed to request mpv log messages" }
            -999
        }
    }

    /** Set the render update callback. JNA auto-converts [Callback] to a native function pointer. */
    fun renderContextSetUpdateCallback(ctx: Pointer?, callback: Callback?, cbCtx: Pointer?) {
        if (ctx == null) return
        try {
            checkAvailable().mpv_render_context_set_update_callback(ctx, callback, cbCtx)
        } catch (e: Exception) {
            logger.warn(e) { "Failed to set mpv render update callback" }
        }
    }

    // -------------------------------------------------------------------------
    // Format constants
    // -------------------------------------------------------------------------

    const val FORMAT_NONE = 0
    const val FORMAT_STRING = 1
    const val FORMAT_OSD_STRING = 2
    const val FORMAT_FLAG = 3
    const val FORMAT_INT64 = 4
    const val FORMAT_DOUBLE = 5
    const val FORMAT_NODE = 6
    const val FORMAT_NODE_ARRAY = 7
    const val FORMAT_NODE_MAP = 8
    const val FORMAT_BYTE_ARRAY = 9

    // -------------------------------------------------------------------------
    // Event ID constants (matches mpv_event_id enum)
    // -------------------------------------------------------------------------

    const val MPV_EVENT_NONE = 0
    const val MPV_EVENT_SHUTDOWN = 1
    const val MPV_EVENT_LOG_MESSAGE = 2
    const val MPV_EVENT_GET_PROPERTY_REPLY = 3
    const val MPV_EVENT_SET_PROPERTY_REPLY = 4
    const val MPV_EVENT_COMMAND_REPLY = 5
    const val MPV_EVENT_START_FILE = 6
    const val MPV_EVENT_END_FILE = 7
    const val MPV_EVENT_FILE_LOADED = 8
    const val MPV_EVENT_CLIENT_MESSAGE = 16
    const val MPV_EVENT_VIDEO_RECONFIG = 17
    const val MPV_EVENT_AUDIO_RECONFIG = 18
    const val MPV_EVENT_SEEK = 20
    const val MPV_EVENT_PLAYBACK_RESTART = 21
    const val MPV_EVENT_PROPERTY_CHANGE = 22
    const val MPV_EVENT_QUEUE_OVERFLOW = 24
    const val MPV_EVENT_HOOK = 25

    // -------------------------------------------------------------------------
    // End file reason constants
    // -------------------------------------------------------------------------

    const val END_FILE_REASON_EOF = 0
    const val END_FILE_REASON_STOP = 2
    const val END_FILE_REASON_QUIT = 3
    const val END_FILE_REASON_ERROR = 4
    const val END_FILE_REASON_REDIRECT = 5

    // -------------------------------------------------------------------------
    // Log level constants
    // -------------------------------------------------------------------------

    const val LOG_LEVEL_NONE = 0
    const val LOG_LEVEL_FATAL = 10
    const val LOG_LEVEL_ERROR = 20
    const val LOG_LEVEL_WARN = 30
    const val LOG_LEVEL_INFO = 40
    const val LOG_LEVEL_V = 50
    const val LOG_LEVEL_DEBUG = 60
    const val LOG_LEVEL_TRACE = 70

    // -------------------------------------------------------------------------
    // Error code constants
    // -------------------------------------------------------------------------

    const val ERROR_SUCCESS = 0
    const val ERROR_EVENT_QUEUE_FULL = -1
    const val ERROR_NOMEM = -2
    const val ERROR_UNINITIALIZED = -3
    const val ERROR_INVALID_PARAMETER = -4
    const val ERROR_OPTION_NOT_FOUND = -5
    const val ERROR_OPTION_FORMAT = -6
    const val ERROR_OPTION_ERROR = -7
    const val ERROR_PROPERTY_NOT_FOUND = -8
    const val ERROR_PROPERTY_FORMAT = -9
    const val ERROR_PROPERTY_UNAVAILABLE = -10
    const val ERROR_PROPERTY_ERROR = -11
    const val ERROR_COMMAND = -12
    const val ERROR_LOADING_FAILED = -13
    const val ERROR_AO_INIT_FAILED = -14
    const val ERROR_VO_INIT_FAILED = -15
    const val ERROR_NOTHING_TO_PLAY = -16
    const val ERROR_UNKNOWN_FORMAT = -17
    const val ERROR_UNSUPPORTED = -18
    const val ERROR_NOT_IMPLEMENTED = -19

    // -------------------------------------------------------------------------
    // Render API constants
    // -------------------------------------------------------------------------

    const val RENDER_PARAM_INVALID = 0
    const val RENDER_PARAM_API_TYPE = 1
    const val RENDER_PARAM_OPENGL_INIT_PARAMS = 2
    const val RENDER_PARAM_OPENGL_FBO = 3
    const val RENDER_PARAM_FLIP_Y = 4
    const val RENDER_PARAM_DEPTH = 5
    const val RENDER_PARAM_ICC_PROFILE = 6
    const val RENDER_PARAM_AMBIENT_LIGHT = 7
    const val RENDER_PARAM_X11 = 8
    const val RENDER_PARAM_WL = 9
    const val RENDER_PARAM_ADVANCED_CONTROL = 10
    const val RENDER_PARAM_NEXT_FRAME_INFO = 11
    const val RENDER_PARAM_BLOCK_FOR_TARGET_TIME = 12
    const val RENDER_PARAM_SKIP_RENDERING = 13
    const val RENDER_PARAM_DRM_DISPLAY = 14
    const val RENDER_PARAM_DRM_DRAW_SURFACE_SIZE = 15
    const val RENDER_PARAM_DRM_DISPLAY_V2 = 16

    // -------------------------------------------------------------------------
    // Software render API constants (added in mpv 0.35.0+)
    // -------------------------------------------------------------------------

    const val RENDER_PARAM_SW_SIZE = 17
    const val RENDER_PARAM_SW_FORMAT = 18
    const val RENDER_PARAM_SW_STRIDE = 19
    const val RENDER_PARAM_SW_POINTER = 20

    // -------------------------------------------------------------------------
    // Render API type constants
    // -------------------------------------------------------------------------

    const val RENDER_API_TYPE_OPENGL = "opengl"
    const val RENDER_API_TYPE_SW = "sw"
    const val RENDER_API_TYPE_LIBPLACEBO = "libplacebo"

    // -------------------------------------------------------------------------
    // Software render pixel format strings
    // -------------------------------------------------------------------------

    const val RENDER_FORMAT_RGB0 = "rgb0"
    const val RENDER_FORMAT_BGR0 = "bgr0"
    const val RENDER_FORMAT_BGRA = "bgra"
    const val RENDER_FORMAT_RGBA = "rgba"
    const val RENDER_FORMAT_0RGB = "0rgb"
    const val RENDER_FORMAT_0BGR = "0bgr"

    // -------------------------------------------------------------------------
    // Default User-Agent used for network streams when none is provided.
    // -------------------------------------------------------------------------

    const val DEFAULT_USER_AGENT =
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36"

    // -------------------------------------------------------------------------
    // Utility helper — JNA structure for double values
    // -------------------------------------------------------------------------

    private class DoubleArrayHolder(value: Double = 0.0) {
        private val memory = Memory(8).also { it.setDouble(0, value) }
        val pointer: Pointer get() = memory
        val value: Double get() = memory.getDouble(0)
    }
}

// -------------------------------------------------------------------------
// JNA Native interface — direct mappings to libmpv C API
// -------------------------------------------------------------------------

private interface MPVNatives : Library {

    /** Create a new mpv instance (mpv_create). */
    fun mpv_create(): Pointer

    /** Initialize an mpv instance (mpv_initialize). */
    fun mpv_initialize(handle: Pointer): Int

    /** Destroy an mpv instance (mpv_destroy). */
    fun mpv_destroy(handle: Pointer)

    /** Set a string option (mpv_set_option_string). */
    fun mpv_set_option_string(handle: Pointer, name: String, value: String): Int

    /** Set a property as string (mpv_set_property_string). */
    fun mpv_set_property_string(handle: Pointer, name: String, value: String): Int

    /** Set a property with format (mpv_set_property). */
    fun mpv_set_property(handle: Pointer, name: String, format: Int, data: Pointer): Int

    /** Get a property with format (mpv_get_property). */
    fun mpv_get_property(handle: Pointer, name: String, format: Int, data: Pointer): Int

    /** Send a command (mpv_command — null-terminated string array). */
    fun mpv_command(handle: Pointer, args: Array<String?>): Int

    /** Observe a property (mpv_observe_property). */
    fun mpv_observe_property(handle: Pointer, replyUserdata: Long, name: String, format: Int): Int

    /** Unobserve a property (mpv_unobserve_property). */
    fun mpv_unobserve_property(handle: Pointer, replyUserdata: Long): Int

    /** Request an event (mpv_request_event). */
    fun mpv_request_event(handle: Pointer, event: Int, enable: Int): Int

    /** Wait for the next event (mpv_wait_event). */
    fun mpv_wait_event(handle: Pointer, timeout: Double): Pointer

    /** Free a string returned by mpv (mpv_free). */
    fun mpv_free(data: Pointer)

    /** Get the client name (mpv_client_name). */
    fun mpv_client_name(handle: Pointer): Pointer

    /** Get the client API version (mpv_client_api_version). */
    fun mpv_client_api_version(): Long

    /** Suspend the main loop (mpv_suspend). */
    fun mpv_suspend(handle: Pointer)

    /** Resume the main loop (mpv_resume). */
    fun mpv_resume(handle: Pointer)

    // -------------------------------------------------------------------------
    // Render API (mpv_render_context_*)
    // -------------------------------------------------------------------------

    /** Create a render context (mpv_render_context_create). */
    fun mpv_render_context_create(res: PointerByReference, mpv: Pointer, params: Pointer): Int

    /** Free a render context (mpv_render_context_free). */
    fun mpv_render_context_free(ctx: Pointer)

    /** Render a frame (mpv_render_context_render). */
    fun mpv_render_context_render(ctx: Pointer, params: Pointer): Int

    /** Set update callback (mpv_render_context_set_update_callback). */
    fun mpv_render_context_set_update_callback(ctx: Pointer, callback: Callback?, cb_ctx: Pointer?)

    /** Request log messages at the given minimum level (mpv_request_log_messages). */
    fun mpv_request_log_messages(ctx: Pointer, min_level: String): Int
}

// -------------------------------------------------------------------------
// MPV Event structure wrapper
// -------------------------------------------------------------------------

/**
 * Wraps a native mpv_event struct pointer for safe Kotlin access.
 *
 * Fields (matching mpv_event C struct):
 * - event_id: Int
 * - error: Int
 * - reply_userdata: Long
 * - data: Pointer (event-specific data)
 */
class MPVEvent(nativePointer: Pointer) {

    val eventId: Int
    val error: Int
    val replyUserdata: Long
    /** End-file reason payload, copied while the MPV event memory is valid. */
    val endFileReason: Int?

    /** End-file error payload, copied while the MPV event memory is valid. */
    val endFileError: Int?

    /** Playlist entry ID from START_FILE or END_FILE payloads. */
    val playlistEntryId: Long?

    /** Snapshot of an observed property's name, or null for other events. */
    val propertyName: String?

    /** Snapshot of an observed property's format, or null for other events. */
    val propertyFormat: Int?

    /** Snapshot of an MPV log message prefix, level name, and text. */
    val logPrefix: String?
    val logLevelName: String?
    val logText: String?
    val logLevel: Int?

    init {
        // mpv_event struct layout (platform-dependent offsets):
        // event_id: 4 bytes at offset 0 (int)
        // error: 4 bytes at offset 4 (int)
        // reply_userdata: 8 bytes at offset 8 (uint64_t)
        // data: pointer at offset 16 (void*)
        eventId = nativePointer.getInt(0)
        error = nativePointer.getInt(4)
        replyUserdata = nativePointer.getLong(8)

        // The data pointer and every payload it references belong to libmpv and
        // are only valid until mpv_wait_event returns the next event. Decode all
        // payloads synchronously here; MPVEvent is later emitted on a Flow and
        // must not expose transient native memory to asynchronous consumers.
        val payload = readPointer(nativePointer, 16)
        endFileReason = if (eventId == MPVLib.MPV_EVENT_END_FILE) payload?.getInt(0) else null
        endFileError = if (eventId == MPVLib.MPV_EVENT_END_FILE) payload?.getInt(4) else null
        playlistEntryId = when (eventId) {
            MPVLib.MPV_EVENT_START_FILE -> payload?.getLong(0)
            MPVLib.MPV_EVENT_END_FILE -> payload?.getLong(8)
            else -> null
        }

        if (eventId == MPVLib.MPV_EVENT_PROPERTY_CHANGE) {
            propertyName = readPointer(payload, 0)?.getString(0)
            propertyFormat = payload?.getInt(8)
        } else {
            propertyName = null
            propertyFormat = null
        }

        if (eventId == MPVLib.MPV_EVENT_LOG_MESSAGE) {
            logPrefix = readPointer(payload, 0)?.getString(0)
            logLevelName = readPointer(payload, 8)?.getString(0)
            logText = readPointer(payload, 16)?.getString(0)
            logLevel = payload?.getInt(24)
        } else {
            logPrefix = null
            logLevelName = null
            logText = null
            logLevel = null
        }
    }

    private fun readPointer(base: Pointer?, offset: Long): Pointer? = try {
        base?.getPointer(offset)?.takeUnless { it == Pointer.NULL }
    } catch (_: NullPointerException) {
        null
    }

    private fun readPointer(base: Pointer, offset: Int): Pointer? = readPointer(base, offset.toLong())

    /** Returns a human-readable name for the event ID. */
    fun eventName(): String = when (eventId) {
        MPVLib.MPV_EVENT_NONE -> "none"
        MPVLib.MPV_EVENT_SHUTDOWN -> "shutdown"
        MPVLib.MPV_EVENT_LOG_MESSAGE -> "log_message"
        MPVLib.MPV_EVENT_START_FILE -> "start_file"
        MPVLib.MPV_EVENT_VIDEO_RECONFIG -> "video_reconfig"
        MPVLib.MPV_EVENT_AUDIO_RECONFIG -> "audio_reconfig"
        MPVLib.MPV_EVENT_SEEK -> "seek"
        MPVLib.MPV_EVENT_PLAYBACK_RESTART -> "playback_restart"
        MPVLib.MPV_EVENT_PROPERTY_CHANGE -> "property_change"
        MPVLib.MPV_EVENT_FILE_LOADED -> "file_loaded"
        MPVLib.MPV_EVENT_END_FILE -> "end_file"
        MPVLib.MPV_EVENT_QUEUE_OVERFLOW -> "queue_overflow"
        MPVLib.MPV_EVENT_HOOK -> "hook"
        else -> "unknown($eventId)"
    }
}

/**
 * Result of loading a file into mpv.
 */
enum class LoadResult {
    SUCCESS,
    FAILED,
    UNSUPPORTED_FORMAT,
}

/**
 * Represents the current playback state.
 */
enum class PlaybackState {
    IDLE,
    LOADING,
    PLAYING,
    PAUSED,
    SEEKING,
    BUFFERING,
    ENDED,
    ERROR,
}
