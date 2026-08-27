package android.widget

import android.content.Context

/**
 * Stub for `android.widget.Toast` on macOS JVM.
 */
open class Toast {

    companion object {
        const val LENGTH_SHORT: Int = 0
        const val LENGTH_LONG: Int = 1

        @JvmStatic
        fun makeText(context: Context, text: CharSequence, duration: Int): Toast = Toast()

        @JvmStatic
        fun makeText(context: Context, resId: Int, duration: Int): Toast = Toast()
    }

    fun show() {
        // No-op on JVM
    }

    fun setText(text: CharSequence) {}

    fun setDuration(duration: Int) {}

    fun cancel() {}
}
