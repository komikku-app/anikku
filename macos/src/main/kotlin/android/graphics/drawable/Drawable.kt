package android.graphics.drawable

/**
 * Stub for `android.graphics.drawable.Drawable` on macOS JVM.
 */
open class Drawable {
    var intrinsicWidth: Int = 0
        private set
    var intrinsicHeight: Int = 0
        private set

    fun setBounds(left: Int, top: Int, right: Int, bottom: Int) {}
    fun draw(canvas: android.graphics.Canvas) {}
    fun setAlpha(alpha: Int) {}
    fun getAlpha(): Int = 255
    fun setColorFilter(colorFilter: Any?) {}
    fun getOpacity(): Int = -3 // PixelFormat.TRANSLUCENT
}
