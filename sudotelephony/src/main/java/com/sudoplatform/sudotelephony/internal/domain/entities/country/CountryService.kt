/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.internal.domain.entities.country

/**
 * Domain service port for country-related telephony operations.
 */
internal interface CountryService {
    /**
     * Fetch the countries the telephony service currently supports.
     *
     * @return [List] of the supported ISO 3166-1 alpha-2 country codes.
     */
    suspend fun getSupportedCountries(): List<String>
}
