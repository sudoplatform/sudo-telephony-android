/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.subscription

import com.sudoplatform.sudotelephony.types.PhoneMessageEvent

/**
 * Subscriber for notifications about the caller's phone messages being received, updated or
 * deleted.
 */
interface PhoneMessageSubscriber : Subscriber {
    /**
     * Notifies the subscriber that one of the caller's phone messages changed.
     *
     * The event carries no message content; fetch the message with
     * [com.sudoplatform.sudotelephony.SudoTelephonyClient.getPhoneMessage] to read its text.
     *
     * @param event [PhoneMessageEvent] The change being reported.
     */
    fun phoneMessageEvent(event: PhoneMessageEvent)
}
