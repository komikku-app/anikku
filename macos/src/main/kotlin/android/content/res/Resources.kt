package android.content.res

import android.graphics.drawable.Drawable

/**
 * Stub for `android.content.res.Resources` on macOS JVM.
 */
open class Resources {
    fun getString(id: Int): String = ""
    fun getString(id: Int, vararg formatArgs: Any): String = ""
    fun getStringArray(id: Int): Array<String> = emptyArray()
    fun getIntArray(id: Int): IntArray = intArrayOf()
    fun getInteger(id: Int): Int = 0
    fun getBoolean(id: Int): Boolean = false
    fun getColor(id: Int): Int = 0
    fun getColor(id: Int, theme: Any?): Int = 0
    fun getDimension(id: Int): Float = 0f
    fun getDimensionPixelSize(id: Int): Int = 0
    fun getDimensionPixelOffset(id: Int): Int = 0
    fun getDrawable(id: Int): Drawable? = null
    fun getDrawable(id: Int, theme: Any?): Drawable? = null
    fun getIdentifier(name: String, defType: String, defPackage: String): Int = 0
    fun openRawResource(id: Int): java.io.InputStream? = null
}
