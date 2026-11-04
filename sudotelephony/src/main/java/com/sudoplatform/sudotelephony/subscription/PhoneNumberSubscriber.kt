/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.subscription

import com.sudoplatform.sudotelephony.types.PhoneNumber

/**
 * Subscriber for notifications about changes to the caller's [PhoneNumber]s.
 *
 * This is how a caller learns that a number being provisioned became
 * [com.sudoplatform.sudotelephony.types.PhoneNumberState.ACTIVE] or
 * [com.sudoplatform.sudotelephony.types.PhoneNumberState.FAILED].
 */
interface PhoneNumberSubscriber : Subscriber {
    /**
     * Notifies the subscriber that one of the caller's phone numbers changed.
     *
     * @param phoneNumber [PhoneNumber] The number as it now stands.
     */
    fun phoneNumberUpdated(phoneNumber: PhoneNumber)
}
