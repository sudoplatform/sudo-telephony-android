/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.internal.domain.useCases.phoneMessage

import com.sudoplatform.sudologging.Logger
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneMessage.DeletePhoneMessagesInput
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneMessage.PhoneMessageBatchResultEntity
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneMessage.PhoneMessageService

/**
 * Application business logic for deleting a batch of the caller's phone messages.
 *
 * @property phoneMessageService [PhoneMessageService] The domain port used to delete.
 * @property logger [Logger] The SDK logger.
 */
internal class DeletePhoneMessagesUseCase(
    private val phoneMessageService: PhoneMessageService,
    private val logger: Logger,
) {
    /**
     * Delete the messages.
     *
     * @param ids [List] IDs of the messages to delete.
     * @return A [PhoneMessageBatchResultEntity] splitting the batch into what was deleted and
     *  what could not be.
     */
    suspend fun execute(ids: List<String>): PhoneMessageBatchResultEntity {
        logger.debug(this::class.java.simpleName)
        return phoneMessageService.delete(DeletePhoneMessagesInput(ids = ids))
    }
}
