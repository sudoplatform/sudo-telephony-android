/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.internal.domain.useCases.phoneMessage

import com.sudoplatform.sudologging.Logger
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneMessage.PhoneMessageBatchResultEntity
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneMessage.PhoneMessageService
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneMessage.UpdatePhoneMessagesInput

/**
 * Input for [UpdatePhoneMessagesUseCase].
 */
internal data class UpdatePhoneMessagesUseCaseInput(
    val ids: List<String>,
    val seen: Boolean,
)

/**
 * Application business logic for changing the read state of a batch of the caller's phone
 * messages.
 *
 * @property phoneMessageService [PhoneMessageService] The domain port used to update.
 * @property logger [Logger] The SDK logger.
 */
internal class UpdatePhoneMessagesUseCase(
    private val phoneMessageService: PhoneMessageService,
    private val logger: Logger,
) {
    /**
     * Update the messages.
     *
     * @param input [UpdatePhoneMessagesUseCaseInput] The messages and the state to set.
     * @return A [PhoneMessageBatchResultEntity] splitting the batch into what was updated and
     *  what could not be.
     */
    suspend fun execute(input: UpdatePhoneMessagesUseCaseInput): PhoneMessageBatchResultEntity {
        logger.debug(this::class.java.simpleName)
        return phoneMessageService.updateSeen(
            UpdatePhoneMessagesInput(ids = input.ids, seen = input.seen),
        )
    }
}
