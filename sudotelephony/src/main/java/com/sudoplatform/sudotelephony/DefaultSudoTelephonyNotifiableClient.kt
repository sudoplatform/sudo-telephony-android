/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony

import com.google.firebase.messaging.RemoteMessage
import com.sudoplatform.sudologging.Logger
import com.sudoplatform.sudonotification.types.NotificationMetaData
import com.sudoplatform.sudonotification.types.NotificationSchemaEntry
import com.sudoplatform.sudotelephony.internal.data.notification.DecodedNotification
import com.sudoplatform.sudotelephony.internal.data.notification.TelephonyNotificationDecoder
import com.sudoplatform.sudotelephony.keys.DeviceKeyManager

/**
 * Default [SudoTelephonyNotifiableClient], which decodes pushes with a
 * [TelephonyNotificationDecoder] and passes them to the app's handler.
 *
 * @property deviceKeyManager [DeviceKeyManager] Holds the phone number keys pushes are sealed
 *  with.
 * @property notificationHandler [SudoTelephonyNotificationHandler] The app's handler.
 * @property logger [Logger] Errors and diagnostic information are logged here.
 */
internal class DefaultSudoTelephonyNotifiableClient(
    deviceKeyManager: DeviceKeyManager,
    private val notificationHandler: SudoTelephonyNotificationHandler,
    private val logger: Logger,
) : SudoTelephonyNotifiableClient {
    private val decoder = TelephonyNotificationDecoder(deviceKeyManager, logger)

    override val serviceName: String = SudoTelephonyClient.CONFIG_TELEPHONY_SERVICE

    override fun getSchema(): NotificationMetaData =
        TelephonyNotificationMetaData(
            serviceName = serviceName,
            schema =
                listOf(
                    TelephonyNotificationSchemaEntry(
                        description = "Type of notification message",
                        fieldName = "meta.type",
                        type = "string",
                    ),
                    TelephonyNotificationSchemaEntry(
                        description = "ID of the phone number the notification is about",
                        fieldName = "meta.phoneNumberId",
                        type = "string",
                    ),
                    TelephonyNotificationSchemaEntry(
                        description = "ID of the Sudo that owns the phone number",
                        fieldName = "meta.sudoId",
                        type = "string",
                    ),
                    TelephonyNotificationSchemaEntry(
                        description = "ID of the conversation the message belongs to",
                        fieldName = "meta.conversationId",
                        type = "string",
                    ),
                    TelephonyNotificationSchemaEntry(
                        description = "ID of the key used to seal the notification content",
                        fieldName = "meta.keyId",
                        type = "string",
                    ),
                ),
        )

    override fun processPayload(message: RemoteMessage) {
        val decoded =
            try {
                decoder.decode(message.data)
            } catch (e: Exception) {
                logger.error("Dropping notification that could not be read: ${e.javaClass.simpleName}")
                null
            } ?: return

        when (decoded) {
            is DecodedNotification.MessageReceived ->
                notificationHandler.onPhoneMessageReceived(decoded.notification)
        }
    }
}

internal data class TelephonyNotificationSchemaEntry(
    override val description: String,
    override val fieldName: String,
    override val type: String,
) : NotificationSchemaEntry

internal data class TelephonyNotificationMetaData(
    override val serviceName: String,
    override val schema: List<TelephonyNotificationSchemaEntry>,
) : NotificationMetaData
