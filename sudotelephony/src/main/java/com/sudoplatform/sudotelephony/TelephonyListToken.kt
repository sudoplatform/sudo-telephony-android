package com.sudoplatform.sudotelephony

/**
 * A token representing a page of items and a reference to the next page
 */
data class TelephonyListToken<T>(
    val items: List<T>,
    val nextToken: String?
)
