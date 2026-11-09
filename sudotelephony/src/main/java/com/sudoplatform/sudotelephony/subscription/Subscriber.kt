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
    sealed class ConnectionState {
        /** Connected and receiving updates. */
        data object Connected : ConnectionState()

        /**
         * Disconnected and will not receive any updates. When disconnected all subscribers
         * are unsubscribed, so the consumer must re-subscribe.
         *
         * @property cause [Throwable] Why the subscription ended, or `null` when it ended
         *  normally. A phone number subscription reports a
         *  [com.sudoplatform.sudotelephony.SudoTelephonyClient.PhoneNumberException] and a
         *  phone message subscription a
         *  [com.sudoplatform.sudotelephony.SudoTelephonyClient.PhoneMessageException]:
         *  `AuthenticationException` when the caller is not authorized, otherwise
         *  `FailedException`.
         */
        data class Disconnected(
            val cause: Throwable? = null,
        ) : ConnectionState()
    }

    /**
     * Notifies the subscriber that the subscription connection state has changed.
     *
     * The subscriber is not notified of changes until the state becomes
     * [ConnectionState.Connected], and stops receiving them when it becomes
     * [ConnectionState.Disconnected]. Unsubscribing is not reported as a disconnection.
     *
     * @param state [ConnectionState] The connection state.
     */
    fun connectionStatusChanged(state: ConnectionState)
}
