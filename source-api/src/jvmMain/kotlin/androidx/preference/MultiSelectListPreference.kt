package androidx.preference

import android.content.Context

class MultiSelectListPreference : Preference {
    constructor(context: Context) : super(context)

    var entries: Array<String>? = null
    var entryValues: Array<String>? = null

    fun setDefaultValue(value: Any?) {}
}
