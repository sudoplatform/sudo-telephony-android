/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.internal.domain.useCases.phoneMessage

import com.sudoplatform.sudologging.Logger
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneMessage.PhoneMessageEntity
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneMessage.PhoneMessageService
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneMessage.PhoneMessageUnsealer

/**
 * Application business logic for retrieving one of the caller's phone messages and making it
 * readable.
 *
 * @property phoneMessageService [PhoneMessageService] The domain port used to retrieve.
 * @property unsealer [PhoneMessageUnsealer] Makes the sealed fields readable.
 * @property logger [Logger] The SDK logger.
 */
internal class GetPhoneMessageUseCase(
    private val phoneMessageService: PhoneMessageService,
    private val unsealer: PhoneMessageUnsealer,
    private val logger: Logger,
) {
    /**
     * Retrieve and unseal the message.
     *
     * @param id [String] ID of the message to retrieve.
     * @return The readable [PhoneMessageEntity], or `null` when it does not belong to the
     *  caller.
     * @throws com.sudoplatform.sudotelephony.SudoTelephonyClient.PhoneMessageException.UnsealingFailedException
     *  if the message could not be unsealed.
     */
    suspend fun execute(id: String): PhoneMessageEntity? {
        logger.debug(this::class.java.simpleName)

        val sealed = phoneMessageService.get(id) ?: return null
        val result = unsealer.unseal(sealed.phoneNumberId, listOf(sealed))
        result.failed.firstOrNull()?.let { throw it.cause }
        return result.unsealed.first()
    }
}
