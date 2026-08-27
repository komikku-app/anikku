package eu.kanade.tachiyomi.util

import kotlinx.coroutines.suspendCancellableCoroutine
import rx.Observable
import rx.Subscriber
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

actual suspend fun <T> Observable<T>.awaitSingle(): T = suspendCancellableCoroutine { cont ->
    cont.unsubscribeOnCancellation(
        subscribe(
            object : Subscriber<T>() {
                override fun onStart() {
                    request(1)
                }

                override fun onNext(t: T) {
                    cont.resume(t)
                }

                override fun onCompleted() {
                    if (cont.isActive) {
                        cont.resumeWithException(
                            IllegalStateException("Should have invoked onNext"),
                        )
                    }
                }

                override fun onError(e: Throwable) {
                    if (!cont.isActive) return
                    cont.resumeWithException(e)
                }
            },
        ),
    )
}

private fun <T> kotlinx.coroutines.CancellableContinuation<T>.unsubscribeOnCancellation(sub: rx.Subscription) {
    invokeOnCancellation { sub.unsubscribe() }
}
