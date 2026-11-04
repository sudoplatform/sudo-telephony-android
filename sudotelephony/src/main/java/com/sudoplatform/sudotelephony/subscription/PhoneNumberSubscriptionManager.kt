/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.subscription

import com.sudoplatform.sudotelephony.types.PhoneNumber

/**
 * Manages subscribers for the phone number update subscription.
 */
internal class PhoneNumberSubscriptionManager<T> : SubscriptionManager<T, PhoneNumberSubscriber>() {
    /**
     * Notifies every subscriber that [phoneNumber] changed.
     *
     * @param phoneNumber [PhoneNumber] The number as it now stands.
     */
    internal fun phoneNumberUpdated(phoneNumber: PhoneNumber) {
        val subscribersToNotify: ArrayList<PhoneNumberSubscriber>
        synchronized(this) {
            subscribersToNotify = ArrayList(this.getSubscribers().values)
        }
        for (subscriber in subscribersToNotify) {
            subscriber.phoneNumberUpdated(phoneNumber)
        }
    }
}
