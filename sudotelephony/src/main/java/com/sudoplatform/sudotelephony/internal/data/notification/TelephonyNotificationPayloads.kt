/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.internal.data.notification

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** JSON settings for push payloads: fields this SDK version doesn't know are ignored. */
internal val NOTIFICATION_JSON =
    Json {
        ignoreUnknownKeys = true
    }

/**
 * The `sudoplatform` entry of a push's data, which the platform notification service adds to
 * every push it delivers.
 *
 * @property servicename [String] The service that sent the push.
 * @property data [String] The service's payload, as a JSON string.
 */
@Serializable
internal data class SudoPlatformEnvelope(
    val servicename: String,
    val data: String,
)

/**
 * The telephony service's push payload. Everything other than [type] and the sealing details
 * is inside [sealed].
 *
 * @property version [Int] Version of the payload format.
 * @property type [String] The notification type, for example [MessageReceivedPayload.TYPE].
 * @property keyId [String] ID of the phone number key the payload is sealed with.
 * @property algorithm [String] Algorithm the payload is sealed with.
 * @property sealed [String] Base64 sealed JSON of the notification's content.
 */
@Serializable
internal data class TelephonyNotificationData(
    val version: Int? = null,
    val type: String,
    val keyId: String? = null,
    val algorithm: String? = null,
    val sealed: String? = null,
) {
    companion object {
        /** The payload format version this SDK was written against. */
        const val SUPPORTED_VERSION = 1
    }
}

/**
 * The unsealed content of a push sent when an inbound message is stored.
 *
 * @property type [String] Always [TYPE].
 * @property owner [String] Subject ID of the user the push is addressed to.
 * @property phoneNumberId [String] ID of the phone number that received the message.
 * @property sudoId [String] ID of the Sudo that owns the phone number, if a Sudo does.
 * @property messageId [String] ID of the received message.
 * @property conversationId [String] ID of the conversation the message belongs to.
 * @property remotePhoneNumber [String] The sender's E.164 number, or a short code.
 * @property bodyPreview [String] The first 140 characters of the message body.
 * @property bodyTruncated [Boolean] Whether the body is longer than [bodyPreview].
 * @property mediaOmitted [Boolean] Whether the message had media, which isn't kept.
 * @property receivedAtEpochMs [Long] When the message was received, in milliseconds since the
 *  Unix epoch.
 */
@Serializable
internal data class MessageReceivedPayload(
    val type: String,
    val owner: String,
    val phoneNumberId: String,
    val sudoId: String? = null,
    val messageId: String,
    val conversationId: String,
    val remotePhoneNumber: String,
    val bodyPreview: String,
    val bodyTruncated: Boolean,
    val mediaOmitted: Boolean,
    val receivedAtEpochMs: Long,
) {
    companion object {
        const val TYPE = "messageReceived"
    }
}

/** The notification types this SDK version can decode. */
internal val KNOWN_NOTIFICATION_TYPES: List<String> = listOf(MessageReceivedPayload.TYPE)
