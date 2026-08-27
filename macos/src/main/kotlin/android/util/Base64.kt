package android.util

/**
 * Stub for `android.util.Base64` on macOS JVM.
 */
object Base64 {
    const val DEFAULT: Int = 0
    const val NO_PADDING: Int = 1
    const val NO_WRAP: Int = 2
    const val CRLF: Int = 4
    const val URL_SAFE: Int = 8
    const val NO_CLOSE: Int = 16

    @JvmStatic
    fun encodeToString(input: ByteArray, flags: Int): String {
        val encoded = java.util.Base64.getEncoder().encodeToString(input)
        return if (flags and NO_WRAP != 0) encoded.replace("\n", "") else encoded
    }

    @JvmStatic
    fun encode(input: ByteArray, flags: Int): ByteArray =
        encodeToString(input, flags).toByteArray()

    @JvmStatic
    fun encode(input: ByteArray, offset: Int, len: Int, flags: Int): ByteArray {
        val slice = if (offset == 0 && len == input.size) input else input.copyOfRange(offset, offset + len)
        return encodeToString(slice, flags).toByteArray()
    }

    @JvmStatic
    fun decode(str: String, flags: Int): ByteArray {
        return java.util.Base64.getDecoder().decode(str)
    }

    @JvmStatic
    fun decode(str: ByteArray, flags: Int): ByteArray {
        return java.util.Base64.getDecoder().decode(str)
    }
}
