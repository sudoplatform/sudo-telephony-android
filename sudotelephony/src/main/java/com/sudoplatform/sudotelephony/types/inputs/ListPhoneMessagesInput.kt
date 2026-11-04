/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.types.inputs

import com.sudoplatform.sudotelephony.types.SortOrder

/**
 * Input object containing the properties used to list a phone number's messages.
 *
 * @property phoneNumberId [String] ID of the phone number whose messages to list.
 * @property conversationId [String] Limits the list to one conversation. When `null` every
 *  conversation of the number is included.
 * @property sortOrder [SortOrder] Order of the messages. When `null` the service returns
 *  newest first.
 * @property limit [Int] Maximum number of items per page. When `null` the service applies
 *  its own default.
 * @property nextToken [String] The token returned by the previous page. Omit for the first
 *  page.
 */
data class ListPhoneMessagesInput(
    val phoneNumberId: String,
    val conversationId: String? = null,
    val sortOrder: SortOrder? = null,
    val limit: Int? = null,
    val nextToken: String? = null,
)
