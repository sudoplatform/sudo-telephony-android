package com.sudoplatform.sudotelephony

import com.amazonaws.mobile.auth.core.internal.util.ThreadUtils.runOnUiThread
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

typealias Callback<T> = (T) -> Unit

typealias Fn0<T> = (Callback<T>) -> Unit
typealias Fn1<A, T> = (A, Callback<T>) -> Unit
typealias Fn2<A, B, T> = (A, B, Callback<T>) -> Unit
typealias Fn3<A, B, C, T> = (A, B, C, Callback<T>) -> Unit
typealias Fn4<A, B, C, D, T> = (A, B, C, D, Callback<T>) -> Unit
typealias Fn5<A, B, C, D, E, T> = (A, B, C, D, E, Callback<T>) -> Unit

/**
 * Wraps a closure with [runOnUiThread] to cause it to be run on the Android main thread.
 */
fun <T> Callback<T>.runOnUiThread(): Callback<T> {
    return {
        runOnUiThread { this(it) }
    }
}

/**
 * awaits on a callback to invoke it in a blocking manner
 */
suspend fun <T> await(block: (Callback<T>) -> Unit): T {
    return suspendCoroutine { cont ->
        block { cont.resume(it) }
    }
}

/**
 * Converts a function of the form f(T)->Unit) to a blocking function f() -> T
 */
fun <T> blocking(fn: Fn0<T>): suspend () -> T = {
    await { fn(it) }
}

/**
 * Converts a function of the form f(A, (T)->Unit) to a blocking function f(A) -> T
 */
fun <A, T> blocking(fn: Fn1<A, T>): suspend (A) -> T = { a: A ->
    await { fn(a, it) }
}

/**
 * Converts a function of the form f(A, B, (T)->Unit) to a blocking function f(A,B) -> T
 */
fun <A, B, T> blocking(fn: Fn2<A, B, T>): suspend (A, B) -> T = { a: A, b: B ->
    await { fn(a, b, it) }
}

/**
 * Converts a function of the form f(A, B, C, (T)->Unit) to a blocking function f(A,B,C) -> T
 */
fun <A, B, C, T> blocking(fn: Fn3<A, B, C, T>): suspend (A, B, C) -> T = { a: A, b: B, c: C ->
    await { fn(a, b, c, it) }
}

/**
 * Converts a function of the form f(A, B, C, D, (T)->Unit) to a blocking function f(A,B,C,D) -> T
 */
fun <A, B, C, D, T> blocking(fn: Fn4<A, B, C, D, T>): suspend (A, B, C, D) -> T = { a: A, b: B, c: C, d: D ->
    await { fn(a, b, c, d, it) }
}

/**
 * Converts a function of the form f(A, B, C, D, E (T)->Unit) to a blocking function f(A,B,C,D,E) -> T
 */
fun <A, B, C, D, E, T> blocking(fn: Fn5<A, B, C, D, E, T>): suspend (A, B, C, D, E) -> T = { a: A, b: B, c: C, d: D, e: E ->
    await { fn(a, b, c, d, e, it) }
}

fun <T> Fn0<T>.toBlocking() = blocking(this)
fun <A, T> Fn1<A, T>.toBlocking() = blocking(this)
fun <A, B, T> Fn2<A, B, T>.toBlocking() = blocking(this)
fun <A, B, C, T> Fn3<A, B, C, T>.toBlocking() = blocking(this)
fun <A, B, C, D, T> Fn4<A, B, C, D, T>.toBlocking() = blocking(this)
fun <A, B, C, D, E, T> Fn5<A, B, C, D, E, T>.toBlocking() = blocking(this)

val String.hexAsByteArray inline get() = this.chunked(2).map { it.toUpperCase().toInt(16).toByte() }.toByteArray()
