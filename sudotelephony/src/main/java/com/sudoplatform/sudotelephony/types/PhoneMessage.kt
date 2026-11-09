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
 * The kind of a phone message.
 */
sealed class PhoneMessageType : Parcelable {
    /** A text message. */
    @Parcelize
    data object Sms : PhoneMessageType()

    /**
     * The service reported a kind this version of the SDK does not recognise.
     *
     * @property value [String] The kind the service reported.
     */
    @Parcelize
    data class Unknown(
        val value: String,
    ) : PhoneMessageType()
}

/**
 * Whether a phone message was received or sent.
 */
sealed class PhoneMessageDirection : Parcelable {
    /** Received on the user's number from the other party. */
    @Parcelize
    data object Inbound : PhoneMessageDirection()

    /**
     * The service reported a direction this version of the SDK does not recognise.
     *
     * @property value [String] The direction the service reported.
     */
    @Parcelize
    data class Unknown(
        val value: String,
    ) : PhoneMessageDirection()
}

/**
 * The order in which a list is returned.
 */
enum class SortOrder {
    /** Oldest first. */
    ASC,

    /** Newest first. */
    DESC,
}

/**
 * A message sent to or from one of the caller's phone numbers.
 *
 * The other party's number and the message text are sealed in transit and unsealed by the SDK
 * with the private key held on this device, so reading a message requires the device that
 * provisioned the number.
 *
 * @property id [String] ID of the message.
 * @property owner [String] ID of the user who owns the message.
 * @property owners [List] Owners taken from the ownership proofs, which tie the message to a
 *  Sudo.
 * @property phoneNumberId [String] ID of the phone number that sent or received the message.
 * @property conversationId [String] ID of the conversation, which groups every message
 *  between the phone number and the other party.
 * @property type [PhoneMessageType] The kind of message.
 * @property direction [PhoneMessageDirection] Whether the message was received or sent.
 * @property localPhoneNumber [String] The user's number in E.164 format.
 * @property remotePhoneNumber [String] The other party's number: the sender of an inbound
 *  message, the recipient of an outbound one. E.164 or a short code. `null` where the number
 *  was withheld.
 * @property body [String] The message text. Empty for a media-only message.
 * @property segmentCount [Int] The number of SMS segments the message was carried in, as
 *  reported by the telephony provider.
 * @property mediaOmitted [Boolean] True for an inbound message whose media was dropped and
 *  only its text kept.
 * @property seen [Boolean] Whether the user has marked the message as read.
 * @property receivedAt [Date] When the service received the message from the telephony
 *  provider. [createdAt] can be later if processing was delayed.
 * @property version [Int] Version of this object, incremented on each update.
 * @property createdAt [Date] When the object was created.
 * @property updatedAt [Date] When the object was last updated.
 */
@Parcelize
data class PhoneMessage(
    val id: String,
    val owner: String,
    val owners: List<Owner>,
    val phoneNumberId: String,
    val conversationId: String,
    val type: PhoneMessageType,
    val direction: PhoneMessageDirection,
    val localPhoneNumber: String,
    val remotePhoneNumber: String? = null,
    val body: String,
    val segmentCount: Int,
    val mediaOmitted: Boolean,
    val seen: Boolean,
    val receivedAt: Date,
    val version: Int,
    val createdAt: Date,
    val updatedAt: Date,
) : Parcelable {
    /** ID of the Sudo that owns the message, or `null` when it belongs only to the user. */
    val sudoId: String?
        get() = owners.sudoId()
}

/**
 * A phone message whose sealed fields could not be unsealed.
 *
 * It carries every field of [PhoneMessage] except the sealed ones, `remotePhoneNumber` and
 * `body`, so it can still be shown, marked as seen or deleted.
 *
 * @property id [String] ID of the message.
 * @property owner [String] ID of the user who owns the message.
 * @property owners [List] Owners taken from the ownership proofs, which tie the message to a
 *  Sudo.
 * @property phoneNumberId [String] ID of the phone number that sent or received the message.
 * @property conversationId [String] ID of the conversation, which groups every message
 *  between the phone number and the other party.
 * @property type [PhoneMessageType] The kind of message.
 * @property direction [PhoneMessageDirection] Whether the message was received or sent.
 * @property localPhoneNumber [String] The user's number in E.164 format.
 * @property segmentCount [Int] The number of SMS segments the message was carried in, as
 *  reported by the telephony provider.
 * @property mediaOmitted [Boolean] True for an inbound message whose media was dropped and
 *  only its text kept.
 * @property seen [Boolean] Whether the user has marked the message as read.
 * @property receivedAt [Date] When the service received the message from the telephony
 *  provider.
 * @property version [Int] Version of this object, incremented on each update.
 * @property createdAt [Date] When the object was created.
 * @property updatedAt [Date] When the object was last updated.
 */
@Parcelize
data class PartialPhoneMessage(
    val id: String,
    val owner: String,
    val owners: List<Owner>,
    val phoneNumberId: String,
    val conversationId: String,
    val type: PhoneMessageType,
    val direction: PhoneMessageDirection,
    val localPhoneNumber: String,
    val segmentCount: Int,
    val mediaOmitted: Boolean,
    val seen: Boolean,
    val receivedAt: Date,
    val version: Int,
    val createdAt: Date,
    val updatedAt: Date,
) : Parcelable {
    /** ID of the Sudo that owns the message, or `null` when it belongs only to the user. */
    val sudoId: String?
        get() = owners.sudoId()
}
