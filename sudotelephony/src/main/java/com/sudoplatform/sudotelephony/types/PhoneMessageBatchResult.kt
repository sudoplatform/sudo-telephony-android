/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.types

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * The outcome of an operation applied to a batch of phone messages.
 *
 * A batch can partially succeed, so a message the service could not act on is reported in
 * [failedIds] rather than failing the whole call.
 *
 * @property succeededIds [List] IDs of the messages the operation applied to.
 * @property failedIds [List] IDs of the messages that were not found or do not belong to the
 *  caller.
 */
@Parcelize
data class PhoneMessageBatchResult(
    val succeededIds: List<String>,
    val failedIds: List<String>,
) : Parcelable
