package android.content

import android.net.Uri

/**
 * Stub for `android.content.Intent` on macOS JVM.
 */
open class Intent {
    var action: String? = null
    var data: Uri? = null
    var type: String? = null
    var packageName: String? = null

    private val extras = mutableMapOf<String, Any?>()
    private val categories = mutableSetOf<String>()
    private val flags = mutableSetOf<Int>()

    constructor()

    constructor(action: String?) {
        this.action = action
    }

    constructor(action: String?, uri: Uri?) {
        this.action = action
        this.data = uri
    }

    fun putExtra(name: String, value: Boolean): Intent = apply { extras[name] = value }
    fun putExtra(name: String, value: Byte): Intent = apply { extras[name] = value }
    fun putExtra(name: String, value: Char): Intent = apply { extras[name] = value }
    fun putExtra(name: String, value: Short): Intent = apply { extras[name] = value }
    fun putExtra(name: String, value: Int): Intent = apply { extras[name] = value }
    fun putExtra(name: String, value: Long): Intent = apply { extras[name] = value }
    fun putExtra(name: String, value: Float): Intent = apply { extras[name] = value }
    fun putExtra(name: String, value: Double): Intent = apply { extras[name] = value }
    fun putExtra(name: String, value: String?): Intent = apply { extras[name] = value }
    fun putExtra(name: String, value: Array<String?>?): Intent = apply { extras[name] = value }
    fun putExtra(name: String, value: Array<Uri>?): Intent = apply { extras[name] = value }

    fun getBooleanExtra(name: String, defaultValue: Boolean): Boolean =
        extras[name] as? Boolean ?: defaultValue
    fun getIntExtra(name: String, defaultValue: Int): Int =
        extras[name] as? Int ?: defaultValue
    fun getLongExtra(name: String, defaultValue: Long): Long =
        extras[name] as? Long ?: defaultValue
    fun getFloatExtra(name: String, defaultValue: Float): Float =
        extras[name] as? Float ?: defaultValue
    fun getDoubleExtra(name: String, defaultValue: Double): Double =
        extras[name] as? Double ?: defaultValue
    fun getStringExtra(name: String): String? = extras[name] as? String
    fun getStringArrayExtra(name: String): Array<String?>? = extras[name] as? Array<String?>
    fun getStringArrayListExtra(name: String): java.util.ArrayList<String>? =
        extras[name] as? java.util.ArrayList<String>
    fun getParcelableExtra(name: String): android.os.Parcelable? =
        extras[name] as? android.os.Parcelable

    fun getExtras(): android.os.Bundle? {
        val bundle = android.os.Bundle()
        extras.forEach { (k, v) ->
            when (v) {
                is Boolean -> bundle.putBoolean(k, v)
                is Int -> bundle.putInt(k, v)
                is Long -> bundle.putLong(k, v)
                is Float -> bundle.putFloat(k, v)
                is Double -> bundle.putDouble(k, v)
                is String -> bundle.putString(k, v)
            }
        }
        return bundle
    }

    fun setDataAndType(data: Uri?, type: String?): Intent = apply {
        this.data = data
        this.type = type
    }

    fun addCategory(category: String) { categories.add(category) }
    fun addFlags(flags: Int) { this.flags.add(flags) }
    fun setFlags(flags: Int) { this.flags.clear(); this.flags.add(flags) }
    fun getFlags(): Int = flags.fold(0) { acc, f -> acc or f }

    companion object {
        const val ACTION_VIEW: String = "android.intent.action.VIEW"
        const val ACTION_MAIN: String = "android.intent.action.MAIN"
        const val ACTION_SEND: String = "android.intent.action.SEND"
        const val ACTION_SENDTO: String = "android.intent.action.SENDTO"
        const val CATEGORY_DEFAULT: String = "android.intent.category.DEFAULT"
        const val CATEGORY_BROWSABLE: String = "android.intent.category.BROWSABLE"
        const val FLAG_ACTIVITY_NEW_TASK: Int = 0x10000000
        const val FLAG_GRANT_READ_URI_PERMISSION: Int = 0x00000001
        const val FLAG_GRANT_WRITE_URI_PERMISSION: Int = 0x00000002
    }
}
