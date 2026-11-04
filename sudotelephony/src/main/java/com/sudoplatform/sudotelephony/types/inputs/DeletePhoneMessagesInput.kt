/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.types.inputs

/**
 * Input object containing the properties used to delete phone messages.
 *
 * @property ids [List] IDs of the messages to delete. The service accepts at most 100 and
 *  rejects a larger batch.
 */
data class DeletePhoneMessagesInput(
    val ids: List<String>,
)
