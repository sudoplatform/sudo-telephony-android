/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.subscription

/**
 * Generic subscriber interface which allows notification of connection state change.
 */
interface Subscriber {
    /**
     * Connection state of the subscription.
     */
    enum class ConnectionState {
        /** Connected and receiving updates. */
        CONNECTED,

        /**
         * Disconnected and will not receive any updates. When disconnected all subscribers
         * are unsubscribed, so the consumer must re-subscribe.
         */
        DISCONNECTED,
    }

    /**
     * Notifies the subscriber that the subscription connection state has changed.
     *
     * The subscriber is not notified of changes until the state becomes
     * [ConnectionState.CONNECTED], and stops receiving them when it becomes
     * [ConnectionState.DISCONNECTED].
     *
     * @param state [ConnectionState] The connection state.
     */
    fun connectionStatusChanged(state: ConnectionState)
}
