package android.app

import android.content.Intent
import android.os.Bundle
import android.view.View

/**
 * Stub for android.app.Activity — provides no-op implementations of lifecycle
 * methods so that compiled extensions that reference Activity can link.
 */
open class Activity {

    open fun onCreate(savedInstanceState: Bundle?) {}

    open fun onStart() {}

    open fun onResume() {}

    open fun onPause() {}

    open fun onStop() {}

    open fun onDestroy() {}

    open fun finish() {}

    open fun startActivity(intent: Intent) {}

    open fun findViewById(id: Int): View? = null

    val intent: Intent? = null
}

class ActivityNotFoundException(name: String) : Exception(name)
