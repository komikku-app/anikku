package android.view

open class View {
    open val rootView: View get() = this

    @Suppress("UNCHECKED_CAST")
    open fun <T : View> findViewById(id: Int): T? = null

    companion object {
        const val VISIBLE: Int = 0
        const val INVISIBLE: Int = 4
        const val GONE: Int = 8
    }
}
