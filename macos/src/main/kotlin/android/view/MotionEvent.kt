package android.view

/**
 * Stub for `android.view.MotionEvent` on macOS JVM.
 */
open class MotionEvent {
    var action: Int = 0
    var x: Float = 0f
    var y: Float = 0f

    fun getRawX(): Float = x
    fun getRawY(): Float = y
    fun getDownTime(): Long = 0L
    fun getEventTime(): Long = 0L
    fun getPointerCount(): Int = 1

    companion object {
        const val ACTION_DOWN: Int = 0
        const val ACTION_UP: Int = 1
        const val ACTION_MOVE: Int = 2
        const val ACTION_CANCEL: Int = 3
        const val ACTION_OUTSIDE: Int = 4
        const val ACTION_POINTER_DOWN: Int = 5
        const val ACTION_POINTER_UP: Int = 6
    }
}
