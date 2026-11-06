/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.types.inputs

import com.sudoplatform.sudotelephony.types.SortOrder

/**
 * Input object containing the properties used to list a conversation's messages.
 *
 * @property conversationId [String] ID of the conversation whose messages to list.
 * @property sortOrder [SortOrder] Order of the messages. When `null` the service returns
 *  newest first.
 * @property limit [Int] Maximum number of items per page. When `null` the service applies
 *  its own default.
 * @property nextToken [String] The token returned by the previous page. Omit for the first
 *  page.
 */
data class ListPhoneMessagesForConversationIdInput(
    val conversationId: String,
    val sortOrder: SortOrder? = null,
    val limit: Int? = null,
    val nextToken: String? = null,
)
