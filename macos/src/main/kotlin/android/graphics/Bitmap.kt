package android.graphics

/**
 * Stub for `android.graphics.Bitmap`.
 */
open class Bitmap(
    val width: Int = 0,
    val height: Int = 0,
) {
    var config: Config? = null
        private set

    private var recycled: Boolean = false

    enum class Config {
        ALPHA_8,
        RGB_565,
        ARGB_4444,
        ARGB_8888,
        RGBA_F16,
        HARDWARE,
    }

    enum class CompressFormat {
        JPEG,
        PNG,
        WEBP,
    }

    fun getPixel(x: Int, y: Int): Int = 0
    fun getByteCount(): Int = width * height * 4
    fun getRowBytes(): Int = width * 4

    fun copy(config: Config?, isMutable: Boolean): Bitmap {
        val b = Bitmap(width, height)
        b.config = config ?: this.config
        return b
    }

    fun recycle() {
        recycled = true
    }

    fun isRecycled(): Boolean = recycled

    fun compress(format: CompressFormat, quality: Int, stream: java.io.OutputStream): Boolean {
        stream.write(ByteArray(0))
        return true
    }

    fun setPixel(x: Int, y: Int, color: Int) {}
    fun getPixels(pixels: IntArray, offset: Int, stride: Int, x: Int, y: Int, width: Int, height: Int) {}
    fun setPixels(pixels: IntArray, offset: Int, stride: Int, x: Int, y: Int, width: Int, height: Int) {}

    companion object {
        fun createBitmap(width: Int, height: Int, config: Config): Bitmap {
            val b = Bitmap(width, height)
            b.config = config
            return b
        }

        fun createBitmap(src: Bitmap): Bitmap {
            val b = Bitmap(src.width, src.height)
            b.config = src.config
            return b
        }

        fun createBitmap(
            width: Int,
            height: Int,
            config: Config,
            hasAlpha: Boolean,
        ): Bitmap {
            val b = Bitmap(width, height)
            b.config = config
            return b
        }

        fun createScaledBitmap(src: Bitmap, dstWidth: Int, dstHeight: Int, filter: Boolean): Bitmap {
            val b = Bitmap(dstWidth, dstHeight)
            b.config = src.config
            return b
        }
    }
}
