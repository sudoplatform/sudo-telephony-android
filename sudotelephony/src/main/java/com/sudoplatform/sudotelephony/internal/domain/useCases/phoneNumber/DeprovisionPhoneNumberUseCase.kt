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
 * Application business logic for releasing a phone number.
 *
 * @property phoneNumberService [PhoneNumberService] The domain port used to release.
 * @property logger [Logger] The SDK logger.
 */
internal class DeprovisionPhoneNumberUseCase(
    private val phoneNumberService: PhoneNumberService,
    private val logger: Logger,
) {
    /**
     * Release the number.
     *
     * @param id [String] ID of the phone number to release.
     * @return The released [PhoneNumberEntity].
     */
    suspend fun execute(id: String): PhoneNumberEntity {
        logger.debug(this::class.java.simpleName)
        return phoneNumberService.deprovision(id)
    }
}
