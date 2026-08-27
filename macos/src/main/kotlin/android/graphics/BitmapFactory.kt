package android.graphics

import java.io.InputStream
import java.io.File

/**
 * Stub for `android.graphics.BitmapFactory` on macOS JVM.
 *
 * Returns a minimal [Bitmap] with non-zero dimensions so callers don't
 * crash with NPE or getWidth/getHeight returning 0.
 */
object BitmapFactory {

    @JvmStatic
    fun decodeByteArray(data: ByteArray, offset: Int, length: Int): Bitmap? {
        if (data.isEmpty() || length <= 0) return null
        return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    }

    @JvmStatic
    fun decodeByteArray(data: ByteArray, offset: Int, length: Int, opts: Any?): Bitmap? {
        return decodeByteArray(data, offset, length)
    }

    @JvmStatic
    fun decodeStream(inputStream: InputStream?): Bitmap? {
        if (inputStream == null) return null
        return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    }

    @JvmStatic
    fun decodeStream(inputStream: InputStream?, outPadding: Any?, opts: Any?): Bitmap? {
        return decodeStream(inputStream)
    }

    @JvmStatic
    fun decodeFile(pathName: String): Bitmap? {
        val file = File(pathName)
        if (!file.exists() || !file.isFile) return null
        return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    }

    @JvmStatic
    fun decodeFile(pathName: String, opts: Any?): Bitmap? = decodeFile(pathName)

    @JvmStatic
    fun decodeResource(res: Any?, id: Int): Bitmap? {
        return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    }

    @JvmStatic
    fun decodeResource(res: Any?, id: Int, opts: Any?): Bitmap? {
        return decodeResource(res, id)
    }
}
