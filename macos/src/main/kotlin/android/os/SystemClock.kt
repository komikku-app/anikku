package android.os

/**
 * Stub for `android.os.SystemClock` on macOS JVM.
 *
 * Used by [RateLimitInterceptor] for elapsed time tracking.
 */
object SystemClock {

    @JvmStatic
    fun elapsedRealtime(): Long = System.nanoTime() / 1_000_000L

    @JvmStatic
    fun elapsedRealtimeNanos(): Long = System.nanoTime()

    @JvmStatic
    fun uptimeMillis(): Long = System.nanoTime() / 1_000_000L

    @JvmStatic
    fun sleep(ms: Long) {
        Thread.sleep(ms)
    }

    @JvmStatic
    fun setCurrentTimeMillis(millis: Long): Boolean = false
}
