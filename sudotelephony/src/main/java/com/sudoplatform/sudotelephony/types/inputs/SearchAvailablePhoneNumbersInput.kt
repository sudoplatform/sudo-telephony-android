/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.types.inputs

/**
 * Input object containing the properties required to search for available phone numbers.
 *
 * @property countryCode [String] ISO 3166-1 alpha-2 country code to search. Must be one of
 *  the codes returned by
 *  [com.sudoplatform.sudotelephony.SudoTelephonyClient.getSupportedCountries].
 * @property areaCode [String] Three-digit area code to search. Currently required: a request
 *  without one is rejected, and so is an area code that is not in use in [countryCode].
 * @property limit [Int] Maximum number of results. When `null` the service returns up to 10.
 *  A limit above 20 is rejected.
 */
data class SearchAvailablePhoneNumbersInput(
    val countryCode: String,
    val areaCode: String? = null,
    val limit: Int? = null,
)
