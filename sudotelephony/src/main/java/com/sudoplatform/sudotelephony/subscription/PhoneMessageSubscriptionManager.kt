/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.subscription

import com.sudoplatform.sudotelephony.types.PhoneMessageEvent

/**
 * Manages subscribers for the phone message event subscription.
 */
internal class PhoneMessageSubscriptionManager<T> : SubscriptionManager<T, PhoneMessageSubscriber>() {
    /**
     * Notifies every subscriber of [event].
     *
     * @param event [PhoneMessageEvent] The change being reported.
     */
    internal fun phoneMessageEvent(event: PhoneMessageEvent) {
        val subscribersToNotify: ArrayList<PhoneMessageSubscriber>
        synchronized(this) {
            subscribersToNotify = ArrayList(this.getSubscribers().values)
        }
        for (subscriber in subscribersToNotify) {
            subscriber.phoneMessageEvent(event)
        }
    }
}
