package android.widget

import android.content.Context
import android.text.TextWatcher
import android.view.View

open class EditText(context: Context) : View() {
    var inputType: Int = 0
    var error: String? = null

    fun addTextChangedListener(watcher: TextWatcher) {}
}
