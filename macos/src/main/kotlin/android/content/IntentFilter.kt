package android.content

/**
 * Stub for `android.content.IntentFilter` on macOS JVM.
 */
open class IntentFilter {
    constructor()

    constructor(action: String) {
        addAction(action)
    }

    private val actions = mutableListOf<String>()

    fun addAction(action: String) { actions.add(action) }
    fun addCategory(category: String) {}
    fun addDataScheme(scheme: String) {}
    fun addDataType(type: String) {}
    fun countActions(): Int = actions.size
    fun getAction(index: Int): String = actions[index]
}
