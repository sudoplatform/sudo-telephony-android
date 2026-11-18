/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.internal.data.notification.transformers

import com.sudoplatform.sudotelephony.internal.data.notification.MessageReceivedPayload
import com.sudoplatform.sudotelephony.types.PhoneMessageReceivedNotification
import java.util.Date

/** Transforms the unsealed content of a message-received push to its public type. */
internal object PhoneMessageReceivedNotificationTransformer {
    fun fromPayloadToApi(payload: MessageReceivedPayload): PhoneMessageReceivedNotification =
        PhoneMessageReceivedNotification(
            messageId = payload.messageId,
            owner = payload.owner,
            sudoId = payload.sudoId,
            phoneNumberId = payload.phoneNumberId,
            conversationId = payload.conversationId,
            remotePhoneNumber = payload.remotePhoneNumber,
            bodyPreview = payload.bodyPreview,
            bodyTruncated = payload.bodyTruncated,
            mediaOmitted = payload.mediaOmitted,
            receivedAt = Date(payload.receivedAtEpochMs),
        )
}
