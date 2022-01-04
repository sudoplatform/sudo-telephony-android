package com.sudoplatform.sudotelephony.telephony

import androidx.annotation.Keep

/**
 * A token representing a page of items and a reference to the next page
 */
@Keep
data class TelephonyListToken<T>(
    val items: List<T>,
    val nextToken: String?
)
