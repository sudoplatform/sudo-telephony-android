package com.sudoplatform.sudotelephony.utils

import com.amazonaws.mobile.auth.core.internal.util.ThreadUtils.runOnUiThread

internal typealias Callback<T> = (T) -> Unit

/**
 * Wraps a closure with [runOnUiThread] to cause it to be run on the Android main thread.
 */
fun <T> Callback<T>.runOnUiThread(): Callback<T> {
    return {
        runOnUiThread { this(it) }
    }
}
