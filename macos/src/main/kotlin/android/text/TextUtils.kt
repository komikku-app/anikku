package android.text

/**
 * Stub for `android.text.TextUtils` on macOS JVM.
 *
 * Provides simple utility methods used by extension and app code.
 */
object TextUtils {

    @JvmStatic
    fun isEmpty(str: CharSequence?): Boolean = str.isNullOrEmpty()

    @JvmStatic
    fun isBlank(str: CharSequence?): Boolean = str.isNullOrBlank()

    @JvmStatic
    fun equals(a: CharSequence?, b: CharSequence?): Boolean = a == b

    @JvmStatic
    fun join(delimiter: CharSequence, tokens: Iterable<*>): String =
        tokens.joinToString(delimiter.toString())

    @JvmStatic
    fun join(delimiter: CharSequence, tokens: Array<out Any>): String =
        tokens.joinToString(delimiter.toString())

    @JvmStatic
    fun htmlEncode(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    @JvmStatic
    fun isDigitsOnly(str: CharSequence): Boolean = str.all { it.isDigit() }

    @JvmStatic
    fun isGraphic(c: Char): Boolean = c.isISOControl().not() && c != ' '

    @JvmStatic
    fun isGraphic(str: CharSequence): Boolean = str.all { isGraphic(it) }

    @JvmStatic
    fun substring(str: CharSequence, start: Int, end: Int): String = str.substring(start, end)
}
