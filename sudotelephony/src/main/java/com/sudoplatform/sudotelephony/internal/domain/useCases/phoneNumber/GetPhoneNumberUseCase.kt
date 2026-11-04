/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.internal.domain.useCases.phoneNumber

import com.sudoplatform.sudologging.Logger
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneNumber.PhoneNumberEntity
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneNumber.PhoneNumberService

/**
 * Application business logic for retrieving one of the caller's phone numbers.
 *
 * @property phoneNumberService [PhoneNumberService] The domain port used to retrieve.
 * @property logger [Logger] The SDK logger.
 */
internal class GetPhoneNumberUseCase(
    private val phoneNumberService: PhoneNumberService,
    private val logger: Logger,
) {
    /**
     * Retrieve the number.
     *
     * @param id [String] ID of the phone number to retrieve.
     * @return The [PhoneNumberEntity], or `null` when it does not belong to the caller.
     */
    suspend fun execute(id: String): PhoneNumberEntity? {
        logger.debug(this::class.java.simpleName)
        return phoneNumberService.get(id)
    }
}
