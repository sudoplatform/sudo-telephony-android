/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.internal.domain.useCases.country

import com.sudoplatform.sudologging.Logger
import com.sudoplatform.sudotelephony.internal.domain.entities.country.CountryService

/**
 * Use case that fetches the countries the telephony service supports.
 *
 * @property countryService [CountryService] The domain port used to retrieve supported countries.
 * @property logger [Logger] The SDK logger.
 */
internal class GetSupportedCountriesUseCase(
    private val countryService: CountryService,
    private val logger: Logger,
) {
    /**
     * Retrieve the supported countries.
     *
     * @return [List] of the supported ISO 3166-1 alpha-2 country codes.
     */
    suspend fun execute(): List<String> {
        logger.debug("Fetching supported countries.")
        return countryService.getSupportedCountries()
    }
}
