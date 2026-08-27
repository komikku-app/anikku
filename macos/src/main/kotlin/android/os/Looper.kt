package android.os

/**
 * Stub for `android.os.Looper` on macOS JVM.
 */
open class Looper {

    companion object {
        @JvmStatic
        fun getMainLooper(): Looper = Looper()

        @JvmStatic
        fun myLooper(): Looper? = Looper()

        @JvmStatic
        fun prepare() {}

        @JvmStatic
        fun loop() {
            // No-op on JVM — never enters the Android message loop
        }

        @JvmStatic
        fun myQueue(): MessageQueue = MessageQueue()
    }

    fun getThread(): Thread = Thread.currentThread()

    fun isCurrentThread(): Boolean = true

    fun quit() {}
}

open class MessageQueue {
    fun addIdleHandler(handler: IdleHandler) {}

    interface IdleHandler {
        fun queueIdle(): Boolean
    }
}

open class Message {
    var what: Int = 0
    var arg1: Int = 0
    var arg2: Int = 0
    var obj: Any? = null
    var target: Handler? = null

    fun sendToTarget() {
        target?.sendMessage(this)
    }

    companion object {
        fun obtain(): Message = Message()
        fun obtain(handler: Handler): Message = Message()
        fun obtain(handler: Handler, what: Int): Message = Message()
        fun obtain(handler: Handler, what: Int, arg1: Int, arg2: Int): Message = Message()
        fun obtain(handler: Handler, what: Int, obj: Any?): Message = Message()
    }
}
