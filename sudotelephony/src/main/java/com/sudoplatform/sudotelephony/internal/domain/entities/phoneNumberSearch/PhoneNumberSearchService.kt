/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.internal.domain.entities.phoneNumberSearch

/**
 * Input for [PhoneNumberSearchService.searchAvailablePhoneNumbers].
 *
 * @property countryCode [String] ISO 3166-1 alpha-2 country code to search.
 * @property areaCode [String] Three-digit area code to search.
 * @property limit [Int] Maximum number of results. When `null` the service applies its own
 *  default.
 */
internal data class SearchAvailablePhoneNumbersInput(
    val countryCode: String,
    val areaCode: String? = null,
    val limit: Int? = null,
)

/**
 * Domain service port for phone number search operations.
 */
internal interface PhoneNumberSearchService {
    /**
     * Search for phone numbers available to provision.
     *
     * @param input [SearchAvailablePhoneNumbersInput] Parameters narrowing the search.
     * @return The [PhoneNumberSearchEntity] holding the available numbers.
     */
    suspend fun searchAvailablePhoneNumbers(input: SearchAvailablePhoneNumbersInput): PhoneNumberSearchEntity
}
