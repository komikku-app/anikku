package androidx.annotation

/**
 * JVM-friendly stub of RequiresApi annotation.
 * Has no effect on JVM — included so extensions that use it can compile.
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class RequiresApi(val value: Int = 1)
