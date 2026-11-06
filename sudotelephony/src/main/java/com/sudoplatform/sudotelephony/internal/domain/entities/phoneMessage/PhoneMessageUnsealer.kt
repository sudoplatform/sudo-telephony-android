/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.internal.domain.entities.phoneMessage

import com.sudoplatform.sudotelephony.internal.domain.entities.common.PartialResultEntity

/**
 * The outcome of unsealing a page of messages.
 *
 * @property unsealed [List] The messages that were unsealed, in the order given.
 * @property failed [List] The messages that could not be unsealed, in the order given, each
 *  with a [com.sudoplatform.sudotelephony.SudoTelephonyClient.PhoneMessageException.UnsealingFailedException]
 *  as its cause.
 */
internal data class UnsealedPhoneMessages(
    val unsealed: List<PhoneMessageEntity>,
    val failed: List<PartialResultEntity<PartialPhoneMessageEntity>>,
)

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
     * per message. A message that cannot be unsealed is returned in
     * [UnsealedPhoneMessages.failed] rather than failing the page.
     *
     * @param phoneNumberId [String] ID of the phone number the messages belong to.
     * @param sealed [List] The messages as the service returned them.
     * @return The [UnsealedPhoneMessages], split into those unsealed and those that failed.
     * @throws com.sudoplatform.sudotelephony.SudoTelephonyClient.PhoneMessageException.PhoneNumberNotFoundException
     *  if the phone number no longer exists.
     * @throws com.sudoplatform.sudotelephony.SudoTelephonyClient.PhoneMessageException.KeyNotFoundException
     *  if this device holds none of the number's keys, so no message on the page could be
     *  unsealed.
     */
    suspend fun unseal(
        phoneNumberId: String,
        sealed: List<SealedPhoneMessageEntity>,
    ): UnsealedPhoneMessages
}
