/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.internal.domain.useCases.phoneNumber

import com.sudoplatform.sudologging.Logger
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneNumber.ListPhoneNumbersForSudoIdInput
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneNumber.ListPhoneNumbersInput
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneNumber.ListPhoneNumbersOutput
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneNumber.PhoneNumberService

/**
 * Input for [ListPhoneNumbersUseCase].
 */
internal data class ListPhoneNumbersUseCaseInput(
    val limit: Int? = null,
    val nextToken: String? = null,
)

/**
 * Input for [ListPhoneNumbersUseCase] when listing the numbers one Sudo owns.
 */
internal data class ListPhoneNumbersForSudoIdUseCaseInput(
    val sudoId: String,
    val limit: Int? = null,
    val nextToken: String? = null,
)

/**
 * Application business logic for listing the caller's phone numbers.
 *
 * @property phoneNumberService [PhoneNumberService] The domain port used to list.
 * @property logger [Logger] The SDK logger.
 */
internal class ListPhoneNumbersUseCase(
    private val phoneNumberService: PhoneNumberService,
    private val logger: Logger,
) {
    /**
     * Retrieve a page of numbers.
     *
     * @param input [ListPhoneNumbersUseCaseInput] Paging parameters.
     * @return A [ListPhoneNumbersOutput] holding the page and its next token.
     */
    suspend fun execute(input: ListPhoneNumbersUseCaseInput): ListPhoneNumbersOutput {
        logger.debug(this::class.java.simpleName)
        return phoneNumberService.list(
            ListPhoneNumbersInput(
                limit = input.limit,
                nextToken = input.nextToken,
            ),
        )
    }

    /**
     * Retrieve a page of the numbers one Sudo owns.
     *
     * @param input [ListPhoneNumbersForSudoIdUseCaseInput] The Sudo and paging parameters.
     * @return A [ListPhoneNumbersOutput] holding the page and its next token.
     */
    suspend fun execute(input: ListPhoneNumbersForSudoIdUseCaseInput): ListPhoneNumbersOutput {
        logger.debug(this::class.java.simpleName)
        return phoneNumberService.listForSudoId(
            ListPhoneNumbersForSudoIdInput(
                sudoId = input.sudoId,
                limit = input.limit,
                nextToken = input.nextToken,
            ),
        )
    }
}
