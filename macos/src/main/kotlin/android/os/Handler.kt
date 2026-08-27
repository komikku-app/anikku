package android.os

/**
 * Stub for `android.os.Handler` on macOS JVM.
 */
open class Handler {

    constructor()
    constructor(callback: Callback)
    constructor(looper: Looper)
    constructor(looper: Looper, callback: Callback)

    interface Callback {
        fun handleMessage(msg: Message): Boolean
    }

    open fun handleMessage(msg: Message) {}

    fun post(r: Runnable): Boolean {
        r.run()
        return true
    }

    fun postDelayed(r: Runnable, delayMillis: Long): Boolean {
        r.run()
        return true
    }

    fun sendMessage(msg: Message): Boolean = true

    fun sendEmptyMessage(what: Int): Boolean = true

    fun sendMessageDelayed(msg: Message, delayMillis: Long): Boolean = true

    fun removeCallbacks(r: Runnable) {}

    fun removeMessages(what: Int) {}

    fun obtainMessage(): Message = Message()

    fun obtainMessage(what: Int): Message = Message()

    companion object {
        fun obtain(msg: Message): Message = Message()
    }
}
