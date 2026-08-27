package android.content

/**
 * Stub for android.content.ActivityNotFoundException used by
 * extension source files that subclass Activity for URL handling
 * (e.g., AnimeStreamUrlActivity, DooPlayUrlActivity).
 */
open class ActivityNotFoundException : RuntimeException {
    constructor() : super()
    constructor(message: String?) : super(message)
    constructor(message: String?, cause: Throwable?) : super(message, cause)
    constructor(cause: Throwable?) : super(cause)
}
