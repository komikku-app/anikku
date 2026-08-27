package app.anikku.macos.platform

import com.sun.jna.Function
import com.sun.jna.Memory
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer

/**
 * Shared JNA bridge for the Objective-C runtime (libobjc).
 *
 * Uses [NativeLibrary.getProcess] to look up symbols from the process's
 * loaded libraries. This avoids the "symbol not found" error that occurs
 * when declaring multiple JNA interface methods all mapping to `objc_msgSend`.
 *
 * Used by [MacOSFullScreen] and [MacOSDockManager] for calling macOS native
 * APIs via the Objective-C message dispatch system.
 */
object ObjC {

    /** Loaded instance of libobjc from the process address space. */
    private val lib: NativeLibrary by lazy {
        try {
            NativeLibrary.getInstance("objc")
        } catch (_: UnsatisfiedLinkError) {
            // Fall back to process-level symbol lookup (covers most JVM setups)
            NativeLibrary.getProcess()
        }
    }

    /** Cached `objc_msgSend` function handle. */
    private val msgSend: Function by lazy {
        lib.getFunction("objc_msgSend")
    }

    // -----------------------------------------------------------------------
    // Symbol lookup
    // -----------------------------------------------------------------------

    /** objc_getClass(const char *name) -> Class */
    fun objc_getClass(className: String): Pointer {
        return lib.getFunction("objc_getClass")
            .invoke(Pointer::class.java, arrayOf(className)) as Pointer
    }

    /** sel_registerName(const char *name) -> SEL */
    fun sel_registerName(name: String): Pointer {
        return lib.getFunction("sel_registerName")
            .invoke(Pointer::class.java, arrayOf(name)) as Pointer
    }

    // -----------------------------------------------------------------------
    // Runtime class creation (for lightweight NSObject subclasses)
    // -----------------------------------------------------------------------

    /** objc_allocateClassPair(Class superclass, const char *name, size_t extraBytes) -> Class */
    fun objc_allocateClassPair(superclass: Pointer, name: String): Pointer {
        return lib.getFunction("objc_allocateClassPair")
            .invoke(Pointer::class.java, arrayOf(superclass, name, 0L)) as Pointer
    }

    /** class_addMethod(Class cls, SEL name, IMP imp, const char *types) -> BOOL */
    fun class_addMethod(cls: Pointer, name: Pointer, imp: Pointer, types: String): Boolean {
        val result = lib.getFunction("class_addMethod")
            .invoke(Int::class.java, arrayOf(cls, name, imp, types)) as Int
        return result != 0
    }

    /** objc_registerClassPair(Class cls) */
    fun objc_registerClassPair(cls: Pointer) {
        lib.getFunction("objc_registerClassPair")
            .invoke(arrayOf(cls))
    }

    // -----------------------------------------------------------------------
    // objc_msgSend — same native symbol, different arity/return-type overloads
    // -----------------------------------------------------------------------

    /** objc_msgSend(id, SEL) -> id */
    fun objc_msgSend(receiver: Pointer, selector: Pointer): Pointer {
        return msgSend.invoke(Pointer::class.java, arrayOf(receiver, selector)) as Pointer
    }

    /** objc_msgSend(id, SEL, id) -> id */
    fun objc_msgSend(receiver: Pointer, selector: Pointer, arg: Pointer): Pointer {
        return msgSend.invoke(Pointer::class.java, arrayOf(receiver, selector, arg)) as Pointer
    }

    /** objc_msgSend(id, SEL) -> void */
    fun objc_msgSend_void(receiver: Pointer, selector: Pointer) {
        msgSend.invoke(arrayOf(receiver, selector))
    }

    /** objc_msgSend(id, SEL, id) -> void */
    fun objc_msgSend_void(receiver: Pointer, selector: Pointer, arg: Pointer) {
        msgSend.invoke(arrayOf(receiver, selector, arg))
    }

    /** objc_msgSend(id, SEL, int64) -> void */
    fun objc_msgSend_void(receiver: Pointer, selector: Pointer, arg: Long) {
        msgSend.invoke(arrayOf(receiver, selector, arg))
    }

    /** objc_msgSend(id, SEL, id, id, id) -> void */
    fun objc_msgSend_void(receiver: Pointer, selector: Pointer, arg1: Pointer, arg2: Pointer, arg3: Pointer) {
        msgSend.invoke(arrayOf(receiver, selector, arg1, arg2, arg3))
    }

    /** objc_msgSend(id, SEL, const char *) -> id */
    fun objc_msgSend_str(receiver: Pointer, selector: Pointer, str: String): Pointer {
        // JNA auto-converts String → const char* when passed as Object
        return msgSend.invoke(Pointer::class.java, arrayOf(receiver, selector, str)) as Pointer
    }

    /** objc_msgSend(id, SEL) -> long — for selectors returning NSInteger */
    fun objc_msgSend_long(receiver: Pointer, selector: Pointer): Long {
        return msgSend.invoke(Long::class.java, arrayOf(receiver, selector)) as Long
    }

    /** objc_msgSend(id, SEL, int64) -> long — for selectors taking an NSInteger arg and returning one */
    fun objc_msgSend_long(receiver: Pointer, selector: Pointer, arg: Long): Long {
        return msgSend.invoke(Long::class.java, arrayOf(receiver, selector, arg)) as Long
    }

    /** objc_msgSend(id, SEL, double) -> id — for NSNumber factory methods. */
    fun objc_msgSend(receiver: Pointer, selector: Pointer, arg: Double): Pointer {
        return msgSend.invoke(Pointer::class.java, arrayOf(receiver, selector, arg)) as Pointer
    }

    /** objc_msgSend(id, SEL) -> double — for selectors returning NSTimeInterval/CGFloat. */
    fun objc_msgSend_double(receiver: Pointer, selector: Pointer): Double {
        return msgSend.invoke(Double::class.java, arrayOf(receiver, selector)) as Double
    }

    /** objc_msgSend(id, SEL, const id[], const id[], NSUInteger) -> id — NSDictionary factories. */
    fun objc_msgSend(
        receiver: Pointer,
        selector: Pointer,
        objects: Array<Pointer>,
        keys: Array<Pointer>,
        count: Long,
    ): Pointer {
        return msgSend.invoke(Pointer::class.java, arrayOf(receiver, selector, objects, keys, count)) as Pointer
    }

    // -----------------------------------------------------------------------
    // Objective-C blocks
    // -----------------------------------------------------------------------

    /** Size of a 64-bit Block_literal_1: isa(8) + flags(4) + reserved(4) + invoke(8) + descriptor(8). */
    private const val BLOCK_LITERAL_SIZE = 32L

    /** Size of a Block_descriptor_1 struct (reserved + size). */
    private const val BLOCK_DESCRIPTOR_SIZE = 16L

    /** `_NSConcreteStackBlock` class pointer, resolved from the process's loaded libraries. */
    private val concreteStackBlock: Pointer by lazy {
        val candidates = listOf("_NSConcreteStackBlock", "__NSConcreteStackBlock")
        for (name in candidates) {
            runCatching { return@lazy lib.getGlobalVariableAddress(name) }
        }
        // Not reachable through libobjc's dependency chain — load libSystem directly.
        val system = runCatching { NativeLibrary.getInstance("System") }
            .getOrElse { NativeLibrary.getInstance("/usr/lib/libSystem.B.dylib") }
        val name = candidates.firstOrNull { candidate ->
            runCatching { system.getGlobalVariableAddress(candidate) }.isSuccess
        } ?: throw UnsatisfiedLinkError("_NSConcreteStackBlock not found")
        system.getGlobalVariableAddress(name)
    }

    /**
     * Shared no-capture block descriptor. IMPORTANT: the `size` field is the
     * TOTAL size of the block literal (header + captures), which [BLOCK_LITERAL_SIZE]
     * for a capture-less block — not the descriptor's own size. `_Block_copy`
     * memmoves exactly this many bytes, so a wrong value leaves the copied
     * block's invoke/descriptor fields as uninitialized garbage and crashes
     * inside `_Block_release`/the handler later.
     */
    private val blockDescriptor: Memory by lazy {
        Memory(BLOCK_DESCRIPTOR_SIZE).apply {
            setLong(0, 0L) // reserved
            setLong(8, BLOCK_LITERAL_SIZE) // total block size (header only, no captures)
        }
    }

    /**
     * Allocate an Objective-C block literal with the given invoke function
     * pointer. The block has no captured variables (flags = 0), so the ObjC
     * runtime copies it with a plain memcpy — no copy/dispose helpers needed.
     *
     * Callers MUST retain the returned [Memory] (and the JNA [Callback] whose
     * function pointer backs [invoke]) for the block's lifetime.
     */
    fun createBlock(invoke: Pointer): Memory {
        val block = Memory(BLOCK_LITERAL_SIZE)
        block.setPointer(0, concreteStackBlock) // isa
        block.setInt(8, 0) // flags: no captures, no copy/dispose, no signature
        block.setInt(12, 0) // reserved
        block.setPointer(16, invoke)
        block.setPointer(24, blockDescriptor)
        return block
    }
}
