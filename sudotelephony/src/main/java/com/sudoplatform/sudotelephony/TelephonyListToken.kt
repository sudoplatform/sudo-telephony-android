package com.sudoplatform.sudotelephony

data class TelephonyListToken<T>(
    val items: List<T>,
    val nextToken: String?
)
