/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.internal.domain.useCases.phoneNumberSearch

import com.sudoplatform.sudologging.Logger
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneNumberSearch.PhoneNumberSearchEntity
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneNumberSearch.PhoneNumberSearchService
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneNumberSearch.SearchAvailablePhoneNumbersInput

/**
 * Input for [SearchAvailablePhoneNumbersUseCase].
 */
internal data class SearchAvailablePhoneNumbersUseCaseInput(
    val countryCode: String,
    val areaCode: String? = null,
    val limit: Int? = null,
)

/**
 * Application business logic for searching the phone numbers available to provision.
 *
 * @property phoneNumberSearchService [PhoneNumberSearchService] The domain port used to run
 *  the search.
 * @property logger [Logger] The SDK logger.
 */
internal class SearchAvailablePhoneNumbersUseCase(
    private val phoneNumberSearchService: PhoneNumberSearchService,
    private val logger: Logger,
) {
    /**
     * Run the search.
     *
     * @param input [SearchAvailablePhoneNumbersUseCaseInput] Parameters narrowing the search.
     * @return The [PhoneNumberSearchEntity] holding the available numbers.
     */
    suspend fun execute(input: SearchAvailablePhoneNumbersUseCaseInput): PhoneNumberSearchEntity {
        logger.debug(this::class.java.simpleName)
        return phoneNumberSearchService.searchAvailablePhoneNumbers(
            SearchAvailablePhoneNumbersInput(
                countryCode = input.countryCode,
                areaCode = input.areaCode,
                limit = input.limit,
            ),
        )
    }
}
