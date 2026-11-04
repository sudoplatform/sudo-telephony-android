/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.types.inputs

/**
 * Input object containing the properties used to change the read state of phone messages.
 *
 * @property ids [List] IDs of the messages to update. The service accepts at most 100 and
 *  rejects a larger batch.
 * @property seen [Boolean] The read state to set on every message in [ids].
 */
data class UpdatePhoneMessagesInput(
    val ids: List<String>,
    val seen: Boolean,
)
