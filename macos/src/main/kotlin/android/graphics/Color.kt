package android.graphics

/**
 * Stub for `android.graphics.Color` on macOS JVM.
 *
 * Provides color constants and ARGB manipulation methods matching
 * the Android Color API.
 */
object Color {

    const val BLACK: Int = 0xFF000000.toInt()
    const val DKGRAY: Int = 0xFF444444.toInt()
    const val GRAY: Int = 0xFF888888.toInt()
    const val LTGRAY: Int = 0xFFCCCCCC.toInt()
    const val WHITE: Int = 0xFFFFFFFF.toInt()
    const val RED: Int = 0xFFFF0000.toInt()
    const val GREEN: Int = 0xFF00FF00.toInt()
    const val BLUE: Int = 0xFF0000FF.toInt()
    const val YELLOW: Int = 0xFFFFFF00.toInt()
    const val CYAN: Int = 0xFF00FFFF.toInt()
    const val MAGENTA: Int = 0xFFFF00FF.toInt()
    const val TRANSPARENT: Int = 0

    @JvmStatic
    fun alpha(color: Int): Int = (color shr 24) and 0xFF

    @JvmStatic
    fun red(color: Int): Int = (color shr 16) and 0xFF

    @JvmStatic
    fun green(color: Int): Int = (color shr 8) and 0xFF

    @JvmStatic
    fun blue(color: Int): Int = color and 0xFF

    @JvmStatic
    fun argb(alpha: Int, red: Int, green: Int, blue: Int): Int {
        return (alpha and 0xFF shl 24) or
                (red and 0xFF shl 16) or
                (green and 0xFF shl 8) or
                (blue and 0xFF)
    }

    @JvmStatic
    fun rgb(red: Int, green: Int, blue: Int): Int = argb(0xFF, red, green, blue)

    @JvmStatic
    fun parseColor(colorString: String): Int {
        return when {
            colorString.startsWith("#") -> {
                val hex = colorString.substring(1)
                when (hex.length) {
                    3 -> {
                        val r = hex[0].toString().repeat(2).toInt(16)
                        val g = hex[1].toString().repeat(2).toInt(16)
                        val b = hex[2].toString().repeat(2).toInt(16)
                        argb(0xFF, r, g, b)
                    }
                    6 -> {
                        val r = hex.substring(0, 2).toInt(16)
                        val g = hex.substring(2, 4).toInt(16)
                        val b = hex.substring(4, 6).toInt(16)
                        argb(0xFF, r, g, b)
                    }
                    8 -> hex.substring(0, 8).toLong(16).toInt()
                    else -> throw IllegalArgumentException("Unknown color: $colorString")
                }
            }
            colorString.equals("red", ignoreCase = true) -> RED
            colorString.equals("green", ignoreCase = true) -> GREEN
            colorString.equals("blue", ignoreCase = true) -> BLUE
            colorString.equals("white", ignoreCase = true) -> WHITE
            colorString.equals("black", ignoreCase = true) -> BLACK
            colorString.equals("transparent", ignoreCase = true) -> TRANSPARENT
            colorString.equals("yellow", ignoreCase = true) -> YELLOW
            colorString.equals("cyan", ignoreCase = true) -> CYAN
            colorString.equals("magenta", ignoreCase = true) -> MAGENTA
            colorString.equals("gray", ignoreCase = true) -> GRAY
            colorString.equals("ltgray", ignoreCase = true) -> LTGRAY
            colorString.equals("dkgray", ignoreCase = true) -> DKGRAY
            else -> throw IllegalArgumentException("Unknown color: $colorString")
        }
    }

    @JvmStatic
    fun HSVToColor(alpha: Int, hsv: FloatArray): Int {
        val h = hsv[0]
        val s = hsv[1]
        val v = hsv[2]
        val c = v * s
        val x = c * (1 - kotlin.math.abs((h / 60) % 2 - 1))
        val m = v - c
        val (r, g, b) = when {
            h < 60 -> Triple(c, x, 0f)
            h < 120 -> Triple(x, c, 0f)
            h < 180 -> Triple(0f, c, x)
            h < 240 -> Triple(0f, x, c)
            h < 300 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        return argb(alpha, ((r + m) * 255).toInt(), ((g + m) * 255).toInt(), ((b + m) * 255).toInt())
    }

    @JvmStatic
    fun toArgb(color: Long): Int = color.toInt()
}
