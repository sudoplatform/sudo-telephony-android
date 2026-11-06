/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.internal.domain.useCases.phoneMessage

import com.sudoplatform.sudologging.Logger
import com.sudoplatform.sudotelephony.internal.domain.entities.common.ListAPIResultEntity
import com.sudoplatform.sudotelephony.internal.domain.entities.common.ListPartialResultEntity
import com.sudoplatform.sudotelephony.internal.domain.entities.common.ListSuccessResultEntity
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneMessage.ListPhoneMessagesForConversationIdInput
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneMessage.ListPhoneMessagesForPhoneNumberIdInput
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneMessage.ListPhoneMessagesOutput
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneMessage.PartialPhoneMessageEntity
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneMessage.PhoneMessageEntity
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneMessage.PhoneMessageService
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneMessage.PhoneMessageUnsealer

/**
 * Application business logic for listing phone messages and making them readable.
 *
 * Every message on a page belongs to the same phone number, so the whole page is handed to
 * the unsealer at once and the sealing key is resolved once.
 *
 * @property phoneMessageService [PhoneMessageService] The domain port used to list.
 * @property unsealer [PhoneMessageUnsealer] Makes the sealed fields readable.
 * @property logger [Logger] The SDK logger.
 */
internal class ListPhoneMessagesUseCase(
    private val phoneMessageService: PhoneMessageService,
    private val unsealer: PhoneMessageUnsealer,
    private val logger: Logger,
) {
    /**
     * Retrieve and unseal a page of a phone number's messages.
     *
     * @param input [ListPhoneMessagesForPhoneNumberIdInput] Which messages to list.
     * @return A [ListAPIResultEntity.Success] when every message was unsealed, otherwise a
     *  [ListAPIResultEntity.Partial] that also holds the messages that could not be.
     */
    suspend fun execute(
        input: ListPhoneMessagesForPhoneNumberIdInput,
    ): ListAPIResultEntity<PhoneMessageEntity, PartialPhoneMessageEntity> {
        logger.debug(this::class.java.simpleName)
        val page = phoneMessageService.listForPhoneNumberId(input)
        return unseal(input.phoneNumberId, page)
    }

    /**
     * Retrieve and unseal a page of a conversation's messages.
     *
     * A conversation belongs to one phone number, which the page's messages identify.
     *
     * @param input [ListPhoneMessagesForConversationIdInput] Which messages to list.
     * @return A [ListAPIResultEntity.Success] when every message was unsealed, otherwise a
     *  [ListAPIResultEntity.Partial] that also holds the messages that could not be.
     */
    suspend fun execute(
        input: ListPhoneMessagesForConversationIdInput,
    ): ListAPIResultEntity<PhoneMessageEntity, PartialPhoneMessageEntity> {
        logger.debug(this::class.java.simpleName)
        val page = phoneMessageService.listForConversationId(input)
        val phoneNumberId =
            page.items.firstOrNull()?.phoneNumberId
                ?: return ListAPIResultEntity.Success(
                    ListSuccessResultEntity(items = emptyList(), nextToken = page.nextToken),
                )
        return unseal(phoneNumberId, page)
    }

    private suspend fun unseal(
        phoneNumberId: String,
        page: ListPhoneMessagesOutput,
    ): ListAPIResultEntity<PhoneMessageEntity, PartialPhoneMessageEntity> {
        val result = unsealer.unseal(phoneNumberId, page.items)
        if (result.failed.isEmpty()) {
            return ListAPIResultEntity.Success(
                ListSuccessResultEntity(items = result.unsealed, nextToken = page.nextToken),
            )
        }
        return ListAPIResultEntity.Partial(
            ListPartialResultEntity(
                items = result.unsealed,
                failed = result.failed,
                nextToken = page.nextToken,
            ),
        )
    }
}
