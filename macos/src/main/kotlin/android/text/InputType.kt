@file:Suppress("unused")

package android.text

/**
 * Stub for [android.text.InputType] on macOS JVM.
 *
 * Provides common input type constants used by extension configuration screens.
 */
object InputType {
    const val TYPE_NULL: Int = 0
    const val TYPE_CLASS_TEXT: Int = 1
    const val TYPE_CLASS_NUMBER: Int = 2
    const val TYPE_CLASS_PHONE: Int = 3
    const val TYPE_CLASS_DATETIME: Int = 4
    const val TYPE_TEXT_VARIATION_NORMAL: Int = 0
    const val TYPE_TEXT_VARIATION_URI: Int = 16
    const val TYPE_TEXT_VARIATION_EMAIL_ADDRESS: Int = 32
    const val TYPE_TEXT_VARIATION_PASSWORD: Int = 128
    const val TYPE_TEXT_VARIATION_VISIBLE_PASSWORD: Int = 144
    const val TYPE_TEXT_FLAG_CAP_SENTENCES: Int = 16384
    const val TYPE_TEXT_FLAG_AUTO_CORRECT: Int = 32768
    const val TYPE_NUMBER_FLAG_DECIMAL: Int = 8192
    const val TYPE_NUMBER_FLAG_SIGNED: Int = 4096
    const val TYPE_DATETIME_VARIATION_NORMAL: Int = 0
    const val TYPE_DATETIME_VARIATION_DATE: Int = 16
    const val TYPE_DATETIME_VARIATION_TIME: Int = 32
}
