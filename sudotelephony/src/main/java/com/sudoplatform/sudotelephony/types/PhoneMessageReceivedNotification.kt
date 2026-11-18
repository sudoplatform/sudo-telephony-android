/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.types

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.Date

/**
 * A push notification that a message was received on one of the user's phone numbers.
 *
 * The notification carries a preview of the body. Fetch the full message with
 * [com.sudoplatform.sudotelephony.SudoTelephonyClient.getPhoneMessage] using [messageId].
 *
 * @property messageId [String] ID of the received message.
 * @property owner [String] ID of the user who owns the message.
 * @property sudoId [String] ID of the Sudo that owns the phone number, or `null` when it belongs
 *  only to the user.
 * @property phoneNumberId [String] ID of the phone number that received the message.
 * @property conversationId [String] ID of the conversation, which groups every message
 *  between the phone number and the other party.
 * @property remotePhoneNumber [String] The sender's number in E.164 format, or a short code.
 * @property bodyPreview [String] The start of the message body, up to 140 characters.
 * @property bodyTruncated [Boolean] Whether the body is longer than [bodyPreview].
 * @property mediaOmitted [Boolean] True when the message had media, which was dropped and only
 *  its text kept.
 * @property receivedAt [Date] When the service received the message from the telephony
 *  provider.
 */
@Parcelize
data class PhoneMessageReceivedNotification(
    val messageId: String,
    val owner: String,
    val sudoId: String?,
    val phoneNumberId: String,
    val conversationId: String,
    val remotePhoneNumber: String,
    val bodyPreview: String,
    val bodyTruncated: Boolean,
    val mediaOmitted: Boolean,
    val receivedAt: Date,
) : Parcelable
