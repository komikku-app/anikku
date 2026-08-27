package androidx.preference

import android.content.Context

open class Preference(val context: Context) {
    var key: String? = null
    var title: String? = null
    var summary: String? = null
    var enabled: Boolean = true
}
