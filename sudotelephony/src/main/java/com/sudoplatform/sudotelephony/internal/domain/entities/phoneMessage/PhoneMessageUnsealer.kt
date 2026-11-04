/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.internal.domain.entities.phoneMessage

/**
 * Domain port that makes sealed phone messages readable.
 *
 * A sealed message carries no key identifier, so an implementation has to work out which key
 * sealed it and whether this device holds the private half.
 */
internal interface PhoneMessageUnsealer {
    /**
     * Unseal every message in [sealed], which must all belong to the phone number identified
     * by [phoneNumberId].
     *
     * Taking the whole page at once lets an implementation resolve the key once rather than
     * per message.
     *
     * @param phoneNumberId [String] ID of the phone number the messages belong to.
     * @param sealed [List] The messages as the service returned them.
     * @return The readable messages, in the order given.
     */
    suspend fun unseal(
        phoneNumberId: String,
        sealed: List<SealedPhoneMessageEntity>,
    ): List<PhoneMessageEntity>
}
