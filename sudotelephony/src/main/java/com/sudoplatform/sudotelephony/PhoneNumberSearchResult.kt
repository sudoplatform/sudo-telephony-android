package com.sudoplatform.sudotelephony

import com.sudoplatform.sudotelephony.fragment.AvailablePhoneNumberResult
import com.sudoplatform.sudotelephony.type.PhoneNumberSearchState

/**
 * A data class wrapper around an E164 phone number search result
 */
data class PhoneNumberSearchResult (val id: String,
                                    val numbers: List<String>,
                                    val countryCode: String,
                                    val gps: AvailablePhoneNumberResult.Gps?,
                                    val prefix: String?,
                                    val state: PhoneNumberSearchState) {

    constructor(data: AvailablePhoneNumberResult): this(data.id(), data.results() ?: emptyList(), data.country(), data.gps(), data.prefix(), data.state())
}

/**
 * A list of countries that are supported
 */
data class SupportedCountriesResult (val countries: List<String>)
