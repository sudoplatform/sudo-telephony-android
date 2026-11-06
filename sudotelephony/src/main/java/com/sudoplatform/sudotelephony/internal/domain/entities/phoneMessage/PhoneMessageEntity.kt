/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.internal.domain.entities.phoneMessage

import com.sudoplatform.sudotelephony.internal.domain.entities.phoneNumber.OwnerEntity
import java.util.Date

/**
 * A phone message whose sealed fields are still sealed, as the service returned it.
 *
 * @property remotePhoneNumber [String] The other party's number, sealed.
 * @property body [String] The message text, sealed.
 */
internal data class SealedPhoneMessageEntity(
    val id: String,
    val owner: String,
    val owners: List<OwnerEntity>,
    val phoneNumberId: String,
    val conversationId: String,
    val type: PhoneMessageTypeEntity,
    val direction: PhoneMessageDirectionEntity,
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
)

/**
 * Core entity representation of a phone message, with its sealed fields unsealed.
 */
internal data class PhoneMessageEntity(
    val id: String,
    val owner: String,
    val owners: List<OwnerEntity>,
    val phoneNumberId: String,
    val conversationId: String,
    val type: PhoneMessageTypeEntity,
    val direction: PhoneMessageDirectionEntity,
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
)

/**
 * Core entity representation of a phone message whose sealed fields could not be unsealed,
 * so it carries only the plaintext fields.
 */
internal data class PartialPhoneMessageEntity(
    val id: String,
    val owner: String,
    val owners: List<OwnerEntity>,
    val phoneNumberId: String,
    val conversationId: String,
    val type: PhoneMessageTypeEntity,
    val direction: PhoneMessageDirectionEntity,
    val localPhoneNumber: String,
    val segmentCount: Int,
    val mediaOmitted: Boolean,
    val seen: Boolean,
    val receivedAt: Date,
    val version: Int,
    val createdAt: Date,
    val updatedAt: Date,
)

/** The kind of a phone message. */
internal enum class PhoneMessageTypeEntity {
    SMS,

    /** The service reported a kind this version of the SDK does not recognise. */
    UNKNOWN,
}

/** Whether a phone message was received or sent. */
internal enum class PhoneMessageDirectionEntity {
    INBOUND,

    /** The service reported a direction this version of the SDK does not recognise. */
    UNKNOWN,
}

/** The order in which a list is returned. */
internal enum class SortOrderEntity {
    ASC,
    DESC,
}
