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
 * The change a [PhoneMessageEvent] is reporting.
 */
enum class PhoneMessageChangeType {
    /** A new message arrived. */
    RECEIVED,

    /** A message changed, for example its seen state. */
    UPDATED,

    /** A message was deleted. */
    DELETED,

    /** The service reported a change this version of the SDK does not recognise. */
    UNKNOWN,
}

/**
 * Notification that one of the caller's phone messages was received, updated or deleted.
 *
 * The event carries no message content, only the change and enough identity to act on it.
 * Fetch the message with
 * [com.sudoplatform.sudotelephony.SudoTelephonyClient.getPhoneMessage] to read its text.
 *
 * @property id [String] ID of the message.
 * @property phoneNumberId [String] ID of the phone number that sent or received the message.
 * @property conversationId [String] ID of the conversation the message belongs to.
 * @property type [PhoneMessageType] The kind of message.
 * @property direction [PhoneMessageDirection] Whether the message was received or sent.
 * @property changeType [PhoneMessageChangeType] The change being reported.
 * @property seen [Boolean] Whether the user has marked the message as read.
 * @property owner [String] ID of the user who owns the message.
 * @property version [Int] Version of the message after the change.
 * @property updatedAt [Date] When the message was last updated.
 */
@Parcelize
data class PhoneMessageEvent(
    val id: String,
    val phoneNumberId: String,
    val conversationId: String,
    val type: PhoneMessageType,
    val direction: PhoneMessageDirection,
    val changeType: PhoneMessageChangeType,
    val seen: Boolean,
    val owner: String,
    val version: Int,
    val updatedAt: Date,
) : Parcelable
