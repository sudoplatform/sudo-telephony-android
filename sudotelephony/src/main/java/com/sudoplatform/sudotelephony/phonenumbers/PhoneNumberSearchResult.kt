package com.sudoplatform.sudotelephony.phonenumbers

import androidx.annotation.Keep
import com.sudoplatform.sudotelephony.graphql.fragment.AvailablePhoneNumberResult
import com.sudoplatform.sudotelephony.graphql.type.PhoneNumberSearchState

/**
 * A data class wrapper around an E164 phone number search result
 */
@Keep
data class PhoneNumberSearchResult(
    val id: String,
    val numbers: List<String>,
    val countryCode: String,
    val gps: AvailablePhoneNumberResult.Gps?,
    val prefix: String?,
    val state: PhoneNumberSearchState
) {

    constructor(data: AvailablePhoneNumberResult) : this(
        data.id(),
        data.results() ?: emptyList(),
        data.country(),
        data.gps(),
        data.prefix(),
        data.state()
    )
}

/**
 * A list of countries that are supported
 */
@Keep
data class SupportedCountriesResult(val countries: List<String>)
