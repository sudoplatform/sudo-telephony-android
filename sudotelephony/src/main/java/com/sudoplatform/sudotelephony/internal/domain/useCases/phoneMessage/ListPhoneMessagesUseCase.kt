/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.internal.domain.useCases.phoneMessage

import com.sudoplatform.sudologging.Logger
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneMessage.ListPhoneMessagesInput
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneMessage.PhoneMessageEntity
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneMessage.PhoneMessageService
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneMessage.PhoneMessageUnsealer
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneMessage.SortOrderEntity

/**
 * Input for [ListPhoneMessagesUseCase].
 */
internal data class ListPhoneMessagesUseCaseInput(
    val phoneNumberId: String,
    val conversationId: String? = null,
    val sortOrder: SortOrderEntity? = null,
    val limit: Int? = null,
    val nextToken: String? = null,
)

/**
 * Output for [ListPhoneMessagesUseCase].
 */
internal data class ListPhoneMessagesUseCaseOutput(
    val items: List<PhoneMessageEntity>,
    val nextToken: String? = null,
)

/**
 * Application business logic for listing a phone number's messages and making them readable.
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
     * Retrieve and unseal a page of messages.
     *
     * @param input [ListPhoneMessagesUseCaseInput] Which messages to list.
     * @return A [ListPhoneMessagesUseCaseOutput] of readable messages and the next token.
     */
    suspend fun execute(input: ListPhoneMessagesUseCaseInput): ListPhoneMessagesUseCaseOutput {
        logger.debug(this::class.java.simpleName)

        val page =
            phoneMessageService.list(
                ListPhoneMessagesInput(
                    phoneNumberId = input.phoneNumberId,
                    conversationId = input.conversationId,
                    sortOrder = input.sortOrder,
                    limit = input.limit,
                    nextToken = input.nextToken,
                ),
            )

        return ListPhoneMessagesUseCaseOutput(
            items = unsealer.unseal(input.phoneNumberId, page.items),
            nextToken = page.nextToken,
        )
    }
}
