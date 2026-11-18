/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony

import com.sudoplatform.sudotelephony.types.PhoneMessageReceivedNotification

/**
 * Receives telephony push notifications decoded by [SudoTelephonyNotifiableClient].
 *
 * Implement the methods for the notifications the app handles; each has an empty default.
 * Push notifications arrive as data-only messages, so the app is responsible for displaying
 * anything to the user.
 *
 * Methods are called on the thread that called
 * [com.sudoplatform.sudonotification.SudoNotificationClient.process], normally the Firebase
 * messaging service's background thread. Exceptions thrown by a method propagate to that caller.
 *
 * @sample com.sudoplatform.sudotelephony.samples.Samples.sudoTelephonyNotificationHandler
 */
interface SudoTelephonyNotificationHandler {
    /**
     * Called when a message is received on one of the user's phone numbers.
     *
     * @param notification [PhoneMessageReceivedNotification] The received message's details,
     *  including a preview of its body.
     */
    fun onPhoneMessageReceived(notification: PhoneMessageReceivedNotification) {}
}
